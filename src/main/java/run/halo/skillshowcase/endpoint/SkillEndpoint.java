package run.halo.skillshowcase.endpoint;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.skillshowcase.dto.GitHubImportRequest;
import run.halo.skillshowcase.dto.SkillVo;
import run.halo.skillshowcase.extension.Skill;
import run.halo.skillshowcase.service.GitHubImportService;
import run.halo.skillshowcase.service.GitHubClient;
import run.halo.skillshowcase.service.SkillService;

@Component
@RequiredArgsConstructor
public class SkillEndpoint implements RouterFunction<ServerResponse> {

    private static final String PREFIX = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

    private final SkillService service;
    private final GitHubImportService importService;
    private final GitHubClient githubClient;

    private final RouterFunction<ServerResponse> delegate = RouterFunctions.route()
        .GET(PREFIX + "/skills", this::list)
        // 具名路由必须注册在 /skills/{name} 之前，否则会被当成 name=github-probe
        .GET(PREFIX + "/net-diag", this::githubProbe)
        .POST(PREFIX + "/skills", this::create)
        .GET(PREFIX + "/skills/{name}", this::get)
        .PUT(PREFIX + "/skills/{name}", this::update)
        .DELETE(PREFIX + "/skills/{name}", this::delete)
        .POST(PREFIX + "/skills/upload", this::uploadZip)
        .PUT(PREFIX + "/skills/{name}/upload", this::updateZip)
        .POST(PREFIX + "/skills/ghimport/preview", this::previewImport)
        .POST(PREFIX + "/skills/ghimport", this::importFromGitHub)
        .POST(PREFIX + "/skills/{name}/check-upstream", this::checkUpstream)
        .POST(PREFIX + "/skills/{name}/sync", this::syncSkill)
        .build();

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return delegate.route(request);
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(service.listAll(), SkillVo.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(Skill.class)
            .flatMap(service::create)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        String name = request.pathVariable("name");
        return service.getByName(name)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.bodyToMono(Skill.class)
            .flatMap(skill -> service.update(name, skill))
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        String name = request.pathVariable("name");
        return service.delete(name)
            .then(ServerResponse.ok().build())
            // 仅「不存在」映射为 404，其余异常（并发、权限等）如实上抛，避免掩盖真实错误
            .onErrorResume(ServerWebInputException.class, e -> ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> uploadZip(ServerRequest request) {
        return request.multipartData().flatMap(parts -> {
            UploadFields f = parseUploadFields(parts);
            if (f.filePart() == null) {
                return ServerResponse.badRequest().bodyValue("missing file part");
            }
            if (f.displayName() == null || f.displayName().isBlank()) {
                return ServerResponse.badRequest().bodyValue("displayName is required");
            }
            return service.uploadZip(f.filePart(), f.displayName(), f.slug(),
                    f.category(), f.summary(), f.coverImage(), f.tags())
                .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
        });
    }

    private Mono<ServerResponse> updateZip(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.multipartData().flatMap(parts -> {
            UploadFields f = parseUploadFields(parts);
            if (f.filePart() == null) {
                return ServerResponse.badRequest().bodyValue("missing file part");
            }
            return service.updateZip(name, f.filePart(), f.displayName(),
                    f.category(), f.summary(), f.tags())
                .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
                .switchIfEmpty(ServerResponse.notFound().build());
        });
    }

    private Mono<ServerResponse> previewImport(ServerRequest request) {
        String queryUrl = request.queryParam("url").orElse(null);
        Mono<String> urlMono = (queryUrl != null && !queryUrl.isBlank())
            ? Mono.just(queryUrl)
            : request.bodyToMono(GitHubImportRequest.class)
                .map(req -> req.url() == null ? "" : req.url());
        return urlMono
            .flatMap(url -> {
                if (url.isBlank()) {
                    return Mono.error(new ServerWebInputException("url is required"));
                }
                return importService.preview(url)
                    .flatMap(preview -> ServerResponse.ok().bodyValue(preview));
            })
            .onErrorResume(e -> {
                if (e instanceof ServerWebInputException swe) {
                    return ServerResponse.badRequest().bodyValue(
                        java.util.Map.of("detail", String.valueOf(swe.getMessage())));
                }
                if (e instanceof org.springframework.core.codec.DecodingException de) {
                    return ServerResponse.badRequest().bodyValue(
                        java.util.Map.of("detail", "请求体解析失败: " + de.getMessage()));
                }
                return Mono.error(e);
            });
    }

    private Mono<ServerResponse> importFromGitHub(ServerRequest request) {
        return request.bodyToMono(GitHubImportRequest.class)
            .flatMap(req -> {
                if (req.url() == null || req.url().isBlank()) {
                    return ServerResponse.badRequest().bodyValue("url is required");
                }
                return importService.importSkill(req.url(), req.slug(), req.displayName(),
                        req.category(), req.summary(), req.tags(), req.published())
                    .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
                    .onErrorResume(e -> {
                        String msg = String.valueOf(e.getMessage());
                        return ServerResponse.badRequest().bodyValue(
                            java.util.Map.of("detail", msg.length() > 300 ? msg.substring(0, 300) : msg));
                    });
            });
    }

    private Mono<ServerResponse> checkUpstream(ServerRequest request) {
        String name = request.pathVariable("name");
        return importService.checkUpstream(name)
            .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    /** Diagnose the server's reachability to GitHub and its mirrors. */
    private Mono<ServerResponse> githubProbe(ServerRequest request) {
        return githubClient.diagnoseChannels()
            .flatMap(base -> ServerResponse.ok().bodyValue(base));
    }

    private Mono<ServerResponse> syncSkill(ServerRequest request) {
        String name = request.pathVariable("name");
        return importService.syncNow(name)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    private record UploadFields(FilePart filePart, String displayName, String slug,
                                String category, String summary, String coverImage,
                                List<String> tags) {}

    private UploadFields parseUploadFields(
            org.springframework.util.MultiValueMap<String, Part> parts) {
        FilePart filePart = null;
        String displayName = null;
        String slug = null;
        String category = null;
        String summary = null;
        String coverImage = null;
        List<String> tags = List.of();

        for (var values : parts.values()) {
            for (Part p : values) {
                if (p instanceof FilePart fp) {
                    filePart = fp;
                } else if (p instanceof FormFieldPart ffp) {
                    String val = ffp.value();
                    switch (ffp.name()) {
                        case "displayName" -> displayName = val;
                        case "slug" -> slug = val;
                        case "category" -> category = val;
                        case "summary" -> summary = val;
                        case "coverImage" -> coverImage = val;
                        case "tags" -> {
                            if (val != null && !val.isBlank()) {
                                tags = List.of(val.split(","));
                            }
                        }
                    }
                }
            }
        }
        return new UploadFields(filePart, displayName, slug, category, summary,
                coverImage, tags);
    }
}
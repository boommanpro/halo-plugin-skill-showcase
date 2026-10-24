package run.halo.skillshowcase.endpoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.skillshowcase.dto.SkillCategoryVo;
import run.halo.skillshowcase.dto.SkillVo;
import run.halo.skillshowcase.extension.Skill;
import run.halo.skillshowcase.service.SkillCategoryService;
import run.halo.skillshowcase.service.SkillService;
import run.halo.skillshowcase.service.ZipExtractorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicShowcaseRouter implements RouterFunction<ServerResponse> {

    private static final String API_PREFIX = "/showcase-api";
    private static final String PUBLIC_PREFIX = "/showcase";

    private final SkillService skillService;
    private final SkillCategoryService categoryService;

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        String path = request.path();
        
        // Public API routes
        if (path.startsWith(API_PREFIX)) {
            return routeApi(request);
        }

        // Public page routes
        if (path.startsWith(PUBLIC_PREFIX)) {
            return routePage(request);
        }

        return Mono.empty();
    }

    private Mono<HandlerFunction<ServerResponse>> routeApi(ServerRequest request) {
        String path = request.path();
        String relativePath = path.substring(API_PREFIX.length());
        
        // Match specific patterns first
        if (relativePath.matches("/skills/[^/]+/related")) {
            String name = extractPathSegment(relativePath, 2);
            request.attributes().put("name", name);
            return Mono.just(this::getRelatedSkills);
        }
        if (relativePath.matches("/skills/[^/]+/tree")) {
            String name = extractPathSegment(relativePath, 2);
            request.attributes().put("name", name);
            return Mono.just(this::getFileTree);
        }
        if (relativePath.matches("/skills/[^/]+/download")) {
            String name = extractPathSegment(relativePath, 2);
            request.attributes().put("name", name);
            return Mono.just(this::downloadZip);
        }
        if (relativePath.matches("/skills/[^/]+/view")) {
            String name = extractPathSegment(relativePath, 2);
            request.attributes().put("name", name);
            return Mono.just(this::countView);
        }
        if (relativePath.matches("/skills/[^/]+")) {
            String name = extractPathSegment(relativePath, 2);
            request.attributes().put("name", name);
            return Mono.just(this::getSkill);
        }
        if (relativePath.equals("/skills") || relativePath.equals("/skills/")) {
            return Mono.just(this::listSkills);
        }
        if (relativePath.equals("/categories") || relativePath.equals("/categories/")) {
            return Mono.just(this::listCategories);
        }
        if (relativePath.equals("/stats") || relativePath.equals("/stats/")) {
            return Mono.just(this::getStats);
        }
        
        return Mono.empty();
    }

    private String extractPathSegment(String path, int segmentIndex) {
        String[] parts = path.split("/");
        // parts[0] is empty string due to leading slash
        if (segmentIndex < parts.length) {
            return parts[segmentIndex];
        }
        return "";
    }

    private Mono<HandlerFunction<ServerResponse>> routePage(ServerRequest request) {
        String path = request.path();
        String relativePath = path.substring(PUBLIC_PREFIX.length());

        // Root showcase page - serve SPA app
        if (relativePath.isEmpty() || relativePath.equals("/")) {
            return Mono.just(this::serveShowcaseApp);
        }

        // SPA routes: /showcase/skill/{name}, /showcase/categories, /showcase/stats, etc.
        // These all serve the SPA shell, which handles routing client-side
        if (relativePath.equals("/categories") || relativePath.equals("/stats") ||
            relativePath.startsWith("/skill/") || relativePath.startsWith("/category/")) {
            return Mono.just(this::serveShowcaseApp);
        }

        // Package skill detail or file: /showcase/{name} or /showcase/{name}/...
        if (relativePath.startsWith("/")) {
            String afterSlash = relativePath.substring(1);
            int nextSlash = afterSlash.indexOf('/');

            if (nextSlash < 0) {
                // /showcase/{name} - check if package skill, otherwise serve SPA
                String name = afterSlash.endsWith("/") ? afterSlash.substring(0, afterSlash.length() - 1) : afterSlash;
                request.attributes().put("name", name);
                return Mono.just(this::serveSkillPageOrApp);
            } else {
                // /showcase/{name}/...
                String name = afterSlash.substring(0, nextSlash);
                String rest = afterSlash.substring(nextSlash + 1);
                request.attributes().put("name", name);
                request.attributes().put("rest", rest);

                if (rest.isEmpty()) {
                    return Mono.just(this::serveSkillPageOrIndex);
                }
                return Mono.just(this::serveShowcaseFile);
            }
        }

        return Mono.empty();
    }

    private Mono<ServerResponse> serveShowcaseApp(ServerRequest request) {
        return loadHtmlResource("showcase/app.html");
    }

    private Mono<ServerResponse> serveSkillPageOrApp(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(skill -> {
                if (SkillService.isPackageType(skill.sourceType())) {
                    return servePackageSkillOrDetail(name);
                }
                return loadHtmlResource("showcase/app.html");
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> serveSkillPage(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(skill -> {
                if (SkillService.isPackageType(skill.sourceType())) {
                    return servePackageSkillOrDetail(name);
                }
                return loadHtmlResource("showcase/detail.html");
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> serveSkillPageOrIndex(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(skill -> {
                if (SkillService.isPackageType(skill.sourceType())) {
                    return servePackageSkillOrDetail(name);
                }
                return loadHtmlResource("showcase/detail.html");
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * A package skill (uploaded ZIP or GitHub import) is either a website package
     * (has index.html — previewed directly) or a skillhub-style package (markdown
     * entry, no index.html — browsed via the VSCode-style detail page).
     */
    private Mono<ServerResponse> servePackageSkillOrDetail(String name) {
        Path dir = skillService.skillDir(name);
        if (!Files.isDirectory(dir)) {
            return ServerResponse.notFound().build();
        }
        Path index = dir.resolve("index.html");
        if (Files.isRegularFile(index)) {
            return writeFile(index);
        }
        return loadHtmlResource("showcase/detail.html");
    }

    private Mono<ServerResponse> listSkills(ServerRequest request) {
        String category = request.queryParam("category").orElse(null);
        if (category != null && !category.isBlank()) {
            return ServerResponse.ok().body(skillService.listPublishedByCategory(category), SkillVo.class);
        }
        return ServerResponse.ok().body(skillService.listPublished(), SkillVo.class);
    }

    private Mono<ServerResponse> getSkill(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> listCategories(ServerRequest request) {
        return ServerResponse.ok().body(categoryService.listAll(), SkillCategoryVo.class);
    }

    private Mono<ServerResponse> getStats(ServerRequest request) {
        return ServerResponse.ok().body(skillService.getStats(), Map.class);
    }

    private Mono<ServerResponse> getRelatedSkills(ServerRequest request) {
        String name = getPathVar(request, "name");
        int limit = request.queryParam("limit").map(PublicShowcaseRouter::parseLimit).orElse(4);
        return ServerResponse.ok().body(skillService.getRelatedSkills(name, limit), SkillVo.class);
    }

    /**
     * 解析 limit 参数：非法输入回退默认值 4，并限制在 [1, 50] 防止异常与超大查询。
     */
    private static int parseLimit(String raw) {
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private Mono<ServerResponse> getFileTree(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .filter(s -> SkillService.isPackageType(s.sourceType()))
            .flatMap(s -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(skillService.buildFileTree(name)))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * Count a detail-page view. GET on purpose (anonymous role template only
     * grants "get" on non-resource URLs); errors never surface to the visitor.
     */
    private Mono<ServerResponse> countView(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.incrementViews(name)
            .flatMap(s -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("views", s.views() != null ? s.views() : 0L)))
            .onErrorResume(e -> {
                log.debug("view count failed for {}: {}", name, e.getMessage());
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of("views", 0L));
            })
            .switchIfEmpty(ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("views", 0L)));
    }

    private Mono<ServerResponse> downloadZip(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(s -> {
                byte[] bytes = skillService.zipContent(toExtension(s));
                String filename = name + "-" + (s.skillVersion() != null ? s.skillVersion() : "v1").replace('/', '_') + ".zip";
                return ServerResponse.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(bytes);
            })
            // increment the download counter in the background; never let counter
            // errors break the download response
            .doOnSuccess(resp -> skillService.incrementDownloads(name)
                .doOnError(err -> log.warn("Failed to increment downloads for {}: {}",
                    name, err.getMessage()))
                .onErrorResume(err -> Mono.empty())
                .subscribe())
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /** Rebuild the minimal Skill extension view zipContent(Skill) expects. */
    private Skill toExtension(SkillVo vo) {
        Skill skill = new Skill();
        skill.getMetadata().setName(vo.name());
        Skill.Spec spec = new Skill.Spec();
        spec.setSourceType(vo.sourceType());
        spec.setContent(vo.content());
        if (vo.files() != null) {
            spec.setFiles(vo.files().stream()
                .map(f -> {
                    Skill.SkillFile sf = new Skill.SkillFile();
                    sf.setPath(f.path());
                    sf.setName(f.name());
                    sf.setLanguage(f.language());
                    sf.setContent(f.content());
                    sf.setSize(f.size());
                    return sf;
                })
                .collect(java.util.stream.Collectors.toList()));
        }
        skill.setSpec(spec);
        return skill;
    }

    private String getPathVar(ServerRequest request, String name) {
        Object val = request.attributes().get(name);
        return val != null ? val.toString() : request.pathVariable(name);
    }

    private Mono<ServerResponse> serveShowcaseFile(ServerRequest request) {
        String name = getPathVar(request, "name");
        String rest = request.attributes().getOrDefault("rest", "").toString();

        if (rest.isEmpty()) {
            return serveSkillPageOrIndex(request);
        }

        String safe = ZipExtractorService.sanitizeForUrlPath(rest);
        if (safe == null) {
            return ServerResponse.badRequest().bodyValue("invalid path");
        }
        // 仅已发布的包技能（ZIP 上传 / GitHub 导入）允许对外提供文件，防止草稿内容被匿名访问
        return skillService.getPublicByName(name)
            .filter(s -> SkillService.isPackageType(s.sourceType()))
            .flatMap(s -> {
                Path dir = skillService.skillDir(name);
                Path target = dir.resolve(safe).normalize();
                if (!target.startsWith(dir) || !Files.exists(target) || Files.isDirectory(target)) {
                    return ServerResponse.notFound().build();
                }
                return writeFile(target);
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> loadHtmlResource(String resourcePath) {
        ClassLoader classLoader = getClass().getClassLoader();
        try (java.io.InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("Resource not found: {}", resourcePath);
                return ServerResponse.notFound().build();
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return ServerResponse.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=utf-8"))
                .bodyValue(content);
        } catch (IOException e) {
            log.error("Failed to load resource: {}", resourcePath, e);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue("Failed to load page");
        }
    }

    private Mono<ServerResponse> writeFile(Path file) {
        Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaType.parseMediaType(
            ZipExtractorService.guessContentType(file.getFileName().toString()));
        return ServerResponse.ok()
            .contentType(mediaType)
            .bodyValue(resource);
    }
}

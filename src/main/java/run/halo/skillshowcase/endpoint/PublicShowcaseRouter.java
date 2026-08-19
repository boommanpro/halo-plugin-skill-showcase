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

        // ZIP skill detail or file: /showcase/{name} or /showcase/{name}/...
        if (relativePath.startsWith("/")) {
            String afterSlash = relativePath.substring(1);
            int nextSlash = afterSlash.indexOf('/');

            if (nextSlash < 0) {
                // /showcase/{name} - check if ZIP skill, otherwise serve SPA
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
                if ("zip".equals(skill.sourceType())) {
                    return serveZipSkillOrDetail(name);
                }
                return loadHtmlResource("showcase/app.html");
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> serveSkillPage(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(skill -> {
                if ("zip".equals(skill.sourceType())) {
                    return serveZipSkillOrDetail(name);
                }
                return loadHtmlResource("showcase/detail.html");
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> serveSkillPageOrIndex(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .flatMap(skill -> {
                if ("zip".equals(skill.sourceType())) {
                    return serveZipSkillOrDetail(name);
                }
                return loadHtmlResource("showcase/detail.html");
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * A ZIP skill is either a website package (has index.html — previewed directly)
     * or a skillhub-style package (markdown entry, no index.html — browsed via the
     * VSCode-style detail page).
     */
    private Mono<ServerResponse> serveZipSkillOrDetail(String name) {
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
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(4);
        return ServerResponse.ok().body(skillService.getRelatedSkills(name, limit), SkillVo.class);
    }

    private Mono<ServerResponse> getFileTree(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .filter(s -> "zip".equals(s.sourceType()))
            .flatMap(s -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(skillService.buildFileTree(name)))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> downloadZip(ServerRequest request) {
        String name = getPathVar(request, "name");
        return skillService.getPublicByName(name)
            .filter(s -> "zip".equals(s.sourceType()))
            .flatMap(s -> {
                byte[] bytes = skillService.zipContent(name);
                String filename = name + "-" + (s.skillVersion() != null ? s.skillVersion() : "v1").replace('/', '_') + ".zip";
                return ServerResponse.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(bytes);
            })
            // increment the download counter in the background
            .doOnSuccess(resp -> skillService.incrementDownloads(name).subscribe())
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> serveShowcaseIndex(ServerRequest request) {
        String name = getPathVar(request, "name");
        Path dir = skillService.skillDir(name);
        if (!Files.exists(dir)) {
            return ServerResponse.notFound().build();
        }
        Path index = dir.resolve("index.html");
        if (!Files.exists(index)) {
            return ServerResponse.notFound().build();
        }
        return writeFile(index);
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
        Path dir = skillService.skillDir(name);
        if (!Files.exists(dir)) {
            return ServerResponse.notFound().build();
        }
        Path target = dir.resolve(safe).normalize();
        if (!target.startsWith(dir) || !Files.exists(target) || Files.isDirectory(target)) {
            return ServerResponse.notFound().build();
        }
        return writeFile(target);
    }

    private Mono<ServerResponse> loadHtmlResource(String resourcePath) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            java.io.InputStream is = classLoader.getResourceAsStream(resourcePath);
            if (is == null) {
                log.error("Resource not found: {}", resourcePath);
                return ServerResponse.notFound().build();
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();
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

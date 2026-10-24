package run.halo.skillshowcase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Thin GitHub API client used by the skill importer: repository metadata, head
 * commit resolution and zipball download. The zipball (not tarball) endpoints are
 * used on purpose so the existing {@link ZipExtractorService} can unpack directly.
 *
 * <p>An optional personal access token can be configured via the ConfigMap
 * {@code skill-showcase-config} (data key {@code githubToken}) to raise the
 * 60 req/h anonymous rate limit; without it, public repos work out of the box.
 */
@Slf4j
@Component
public class GitHubClient {

    public static final String CONFIG_MAP_NAME = "skill-showcase-config";
    private static final String TOKEN_KEY = "githubToken";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    /** Zip 下载的内存缓冲上限（WebClient 默认仅 256KB，必须放大才能拉仓库包）。 */
    private static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;

    private static final Pattern FULL_URL_PATTERN = Pattern.compile(
        "^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)"
            + "(?:/(?:tree|blob)/([A-Za-z0-9_./-]+))?(?:\\.git)?/?$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_PATTERN = Pattern.compile(
        "^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$");

    private final WebClient api;
    private final WebClient archive;
    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile long tokenFetchedAtMillis;

    public GitHubClient(ReactiveExtensionClient client) {
        this.client = client;
        this.api = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader(HttpHeaders.USER_AGENT, "halo-plugin-skill-showcase")
            // git-trees 递归响应可到几百 KB，默认 256KB 会截断
            .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
            .build();
        this.archive = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, "halo-plugin-skill-showcase")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_ARCHIVE_BYTES))
            .build();
    }

    /** Parsed GitHub source: owner/repo plus optional ref and subdirectory. */
    public record SourceRef(String owner, String repo, String ref, String subdir) {
        public String repoSlug() {
            return owner + "/" + repo;
        }
    }

    /**
     * Accepts https://github.com/owner/repo[/tree/ref[/subdir]], git@…, owner/repo
     * shorthand and a trailing {@code ?subdir=…} query on the shorthand form.
     */
    public static SourceRef parseSourceUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ServerWebInputException("GitHub 地址不能为空");
        }
        String input = raw.trim();
        String subdirFromQuery = null;
        int query = input.indexOf('?');
        if (query >= 0) {
            String qs = input.substring(query + 1);
            input = input.substring(0, query);
            for (String pair : qs.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equalsIgnoreCase("subdir")) {
                    subdirFromQuery = kv[1];
                }
            }
        }
        input = input.replaceFirst("^git@github\\.com:", "https://github.com/");

        Matcher full = FULL_URL_PATTERN.matcher(input);
        if (full.matches()) {
            String rest = full.group(3);
            String ref = null;
            String subdir = subdirFromQuery;
            if (rest != null && !rest.isBlank()) {
                int slash = rest.indexOf('/');
                if (slash < 0) {
                    ref = rest;
                } else {
                    ref = rest.substring(0, slash);
                    subdir = rest.substring(slash + 1);
                }
            }
            return new SourceRef(full.group(1), full.group(2), emptyToNull(ref),
                emptyToNull(subdir));
        }
        Matcher shortHand = SHORT_PATTERN.matcher(input);
        if (shortHand.matches()) {
            return new SourceRef(shortHand.group(1), shortHand.group(2), null,
                emptyToNull(subdirFromQuery));
        }
        throw new ServerWebInputException(
            "无法识别的 GitHub 地址，支持 https://github.com/owner/repo 或 owner/repo");
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null
            : (s.endsWith("/") ? s.substring(0, s.length() - 1) : s);
    }

    public record RepoMeta(String defaultBranch, String description, String homepage,
                           String ownerLogin, String ownerAvatar, Long stars) {}

    public Mono<RepoMeta> fetchRepoMeta(SourceRef source) {
        return getJson(api, "/repos/{o}/{r}", source.owner(), source.repo())
            .timeout(REQUEST_TIMEOUT)
            .map(this::parseRepoMeta)
            .onErrorMap(this::mapError);
    }

    /** Resolve a ref (branch / tag / sha) to the head commit sha. */
    public Mono<String> fetchHeadCommit(SourceRef source) {
        String ref = source.ref() != null ? source.ref() : "HEAD";
        return getJson(api, "/repos/{o}/{r}/commits/{ref}",
                source.owner(), source.repo(), ref)
            .timeout(REQUEST_TIMEOUT)
            .map(json -> {
                try {
                    JsonNode node = objectMapper.readTree(json);
                    JsonNode sha = node.get("sha");
                    if (sha == null || sha.isNull()) {
                        throw new ServerWebInputException("无法解析上游 commit");
                    }
                    return sha.asText();
                } catch (ServerWebInputException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ServerWebInputException("无法解析上游 commit：" + e.getMessage());
                }
            })
            .onErrorMap(this::mapError);
    }

    /** A single repository file (path relative to repo root, size in bytes). */
    public record GhFile(String path, long size) {}

    /**
     * List all files of the repo at the given commit via the git trees API —
     * one small request against api.github.com, which stays reachable and fast
     * even where codeload bandwidth is unusable.
     */
    public Mono<List<GhFile>> listFiles(SourceRef source, String sha) {
        return getJson(api, "/repos/{o}/{r}/git/trees/{sha}?recursive=1",
                source.owner(), source.repo(), sha)
            .timeout(REQUEST_TIMEOUT)
            .map(json -> {
                try {
                    JsonNode root = objectMapper.readTree(json);
                    if (root.path("truncated").asBoolean(false)) {
                        throw new ServerWebInputException(
                            "仓库文件树过大（API 截断），请改用单技能仓库");
                    }
                    List<GhFile> files = new java.util.ArrayList<>();
                    for (JsonNode node : root.path("tree")) {
                        if (!"blob".equals(node.path("type").asText())) {
                            continue;
                        }
                        String path = node.path("path").asText(null);
                        if (path == null || path.isEmpty()) {
                            continue;
                        }
                        files.add(new GhFile(path, node.path("size").asLong(0)));
                    }
                    if (files.isEmpty()) {
                        throw new ServerWebInputException("仓库中没有文件");
                    }
                    return files;
                } catch (ServerWebInputException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ServerWebInputException("无法解析仓库文件树：" + e.getMessage());
                }
            })
            .onErrorMap(this::mapError);
    }

    /**
     * Fetch one file: raw.githubusercontent.com first (diagnosed fast on this
     * host class, no API rate limit, full sha support), GitHub contents API
     * (base64) as fallback. Uses a non-template URI so exotic file names
     * ({, } etc.) survive.
     */
    public Mono<byte[]> fetchFile(SourceRef source, String sha, String path) {
        String url = "https://raw.githubusercontent.com/" + source.owner() + "/"
            + source.repo() + "/" + sha + "/" + path;
        return archive.get().uri(URI.create(url))
            .retrieve()
            .bodyToMono(byte[].class)
            .timeout(Duration.ofSeconds(20))
            .filter(bytes -> bytes.length > 0)
            .onErrorResume(e -> fetchFileViaApi(source, sha, path));
    }

    private Mono<byte[]> fetchFileViaApi(SourceRef source, String sha, String path) {
        return getJson(api, "/repos/{o}/{r}/contents/{p}?ref={sha}",
                source.owner(), source.repo(), path, sha)
            .timeout(REQUEST_TIMEOUT)
            .map(json -> {
                try {
                    JsonNode node = objectMapper.readTree(json);
                    String encoding = node.path("encoding").asText("");
                    String content = node.path("content").asText("");
                    if (!"base64".equals(encoding) || content.isEmpty()) {
                        throw new ServerWebInputException("文件获取失败：" + path);
                    }
                    return java.util.Base64.getDecoder().decode(content.replace("\n", ""));
                } catch (ServerWebInputException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ServerWebInputException("文件解码失败：" + path);
                }
            });
    }

    /**
     * Probe every candidate GitHub channel with a small known file and report
     * reachability + latency. Used to diagnose hosts with restricted egress
     * (typical for CN servers) before choosing a download strategy.
     */
    public Mono<Map<String, Object>> diagnoseChannels() {
        String testPath = "https://raw.githubusercontent.com/anthropics/skills/main/README.md";
        record Channel(String name, String url, boolean viaApi) {}
        List<Channel> channels = List.of(
            new Channel("api.github.com", "https://api.github.com/repos/anthropics/skills", false),
            new Channel("codeload.github.com",
                "https://codeload.github.com/anthropics/skills/zip/refs/heads/main", false),
            new Channel("raw.githubusercontent.com", testPath, false),
            new Channel("cdn.jsdelivr.net",
                "https://cdn.jsdelivr.net/gh/anthropics/skills@main/README.md", false),
            new Channel("gh-proxy.com", "https://gh-proxy.com/" + testPath, false),
            new Channel("ghproxy.net", "https://ghproxy.net/" + testPath, false),
            new Channel("ghfast.top", "https://ghfast.top/" + testPath, false),
            new Channel("github.com", "https://github.com/anthropics/skills/archive/refs/heads/main.zip", false));
        return Flux.fromIterable(channels)
            .flatMap(ch -> {
                long start = System.currentTimeMillis();
                return WebClient.create().get().uri(URI.create(ch.url()))
                    .exchangeToMono(resp -> resp.bodyToMono(byte[].class)
                        .map(bytes -> Map.<String, Object>of(
                            "ok", true,
                            "status", resp.statusCode().value(),
                            "ms", System.currentTimeMillis() - start,
                            "bytes", bytes.length))
                        .onErrorResume(e -> Mono.just(Map.<String, Object>of(
                            "ok", true, "status", resp.statusCode().value(),
                            "ms", System.currentTimeMillis() - start, "bytes", 0))))
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> Mono.just(Map.<String, Object>of(
                        "ok", false,
                        "ms", System.currentTimeMillis() - start,
                        "error", String.valueOf(e.getMessage()).substring(0,
                            Math.min(120, String.valueOf(e.getMessage()).length())))))
                    .map(result -> Map.entry(ch.name(), result));
            }, 3)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .map(m -> {
                Map<String, Object> out = new java.util.LinkedHashMap<>(m);
                out.put("probedAt", Instant.now().toString());
                return out;
            })
            // 再实测导入流程的两个真实步骤（trees 列表 + raw 单文件下载）
            .flatMap(base -> {
                SourceRef src = new SourceRef("anthropics", "skills", null, null);
                long t1 = System.currentTimeMillis();
                return listFiles(src, "main")
                    .map(files -> {
                        base.put("step.listFiles", Map.of(
                            "ok", true,
                            "ms", System.currentTimeMillis() - t1,
                            "files", files.size()));
                        return base;
                    })
                    .onErrorResume(e -> {
                        base.put("step.listFiles", Map.of(
                            "ok", false,
                            "ms", System.currentTimeMillis() - t1,
                            "error", truncate(String.valueOf(e.getMessage()), 100)));
                        return Mono.just(base);
                    })
                    .flatMap(b -> {
                        long t2 = System.currentTimeMillis();
                        return fetchFile(src, "main", "skills/docx/SKILL.md")
                            .map(bytes -> {
                                b.put("step.fetchFile", Map.of(
                                    "ok", true,
                                    "ms", System.currentTimeMillis() - t2,
                                    "bytes", bytes.length));
                                return b;
                            })
                            .onErrorResume(e -> {
                                b.put("step.fetchFile", Map.of(
                                    "ok", false,
                                    "ms", System.currentTimeMillis() - t2,
                                    "error", truncate(String.valueOf(e.getMessage()), 100)));
                                return Mono.just(b);
                            });
                    });
            });
    }


    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    private Mono<String> getJson(WebClient webClient, String uri, Object... uriVars) {
        return request(webClient, uri, uriVars).flatMap(spec -> spec.retrieve()
            .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> githubError(resp.statusCode().value(), body)))
            .bodyToMono(String.class));
    }

    private Mono<byte[]> getBytes(WebClient webClient, String uri, Object... uriVars) {
        return request(webClient, uri, uriVars).flatMap(spec -> spec.retrieve()
            .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> githubError(resp.statusCode().value(), body)))
            .bodyToMono(byte[].class));
    }

    private Mono<WebClient.RequestHeadersSpec<?>> request(WebClient webClient, String uri,
                                                          Object... uriVars) {
        return resolveToken().map(token -> {
            WebClient.RequestHeadersSpec<?> spec = webClient.get().uri(uri, uriVars);
            if (token != null && !token.isBlank()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            return spec;
        });
    }

    private Throwable githubError(int status, String body) {
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 404) {
            return new ServerWebInputException("GitHub 仓库不存在或不可访问（404）");
        }
        if (status == 403 && lower.contains("rate limit")) {
            return new ServerWebInputException(
                "GitHub API 触发限流（匿名 60 次/小时）。可在 ConfigMap "
                    + CONFIG_MAP_NAME + " 配置 " + TOKEN_KEY + " 提升额度");
        }
        if (status == 401) {
            return new ServerWebInputException("GitHub Token 无效或已过期（401）");
        }
        return new ServerWebInputException("GitHub API 错误（HTTP " + status + "）");
    }

    private Throwable mapError(Throwable e) {
        if (e instanceof ServerWebInputException) {
            return e;
        }
        return new ServerWebInputException("访问 GitHub 失败：" + e.getMessage());
    }

    private RepoMeta parseRepoMeta(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode owner = node.path("owner");
            return new RepoMeta(
                node.path("default_branch").asText("main"),
                textOrNull(node.get("description")),
                textOrNull(node.get("homepage")),
                textOrNull(owner.get("login")),
                textOrNull(owner.get("avatar_url")),
                node.path("stargazers_count").asLong(0));
        } catch (Exception e) {
            throw new ServerWebInputException("无法解析 GitHub 仓库信息");
        }
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    /**
     * Token from ConfigMap, cached for 5 minutes so each import is not an extra
     * read. Always emits "" when no token is configured — a null emission would
     * blow up the reactive chain (reactor rejects null map results).
     */
    private Mono<String> resolveToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now - tokenFetchedAtMillis < 5 * 60 * 1000L) {
            return Mono.just(cachedToken);
        }
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .map(cm -> cm.getData() != null ? cm.getData().get(TOKEN_KEY) : null)
            .map(t -> (t == null || t.isBlank()) ? "" : t.trim())
            .defaultIfEmpty("")
            .doOnNext(t -> {
                cachedToken = t.isEmpty() ? null : t;
                tokenFetchedAtMillis = now;
            })
            .onErrorResume(e -> {
                log.debug("[skill-showcase] failed to read github token config: {}",
                    e.getMessage());
                return Mono.just("");
            });
    }
}

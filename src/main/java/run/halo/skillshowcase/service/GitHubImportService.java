package run.halo.skillshowcase.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.skillshowcase.dto.SkillVo;
import run.halo.skillshowcase.extension.Skill;

/**
 * Import skills from GitHub repositories (SKILL.md-style packages) and keep them
 * in sync with upstream. Imported skills behave exactly like uploaded ZIP
 * packages (files on disk, browsable tree, downloadable zip) but carry the extra
 * {@code sourceRepo}/{@code upstreamCommit} metadata for update detection.
 *
 * <p>Download strategy (diagnosed against the host network): file list via the
 * GitHub git-trees API, then per-file download via raw.githubusercontent.com
 * with the contents API as fallback — codeload zipballs are unusably slow on
 * hosts with restricted GitHub bandwidth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubImportService {

    private final GitHubClient github;
    private final ZipStorageService storage;
    private final ReactiveExtensionClient client;
    private final SkillService skillService;

    /** What the console import wizard shows after parsing a GitHub URL. */
    public record ImportPreview(
        String repoSlug, String ref, String resolvedSha, String defaultBranch,
        String suggestedSlug, String displayName, String summary, String version,
        String entryFile, Integer fileCount, Long extractedSize,
        String author, String authorAvatar, String authorUrl, String homepage,
        Long stars, boolean websitePackage) {}

    /** Result of comparing the local copy with the upstream head. */
    public record UpstreamStatus(boolean hasUpdate, String currentCommit,
                                 String upstreamCommit, String upstreamVersion,
                                 String message) {}

    private record FetchedPackage(GitHubClient.SourceRef source,
                                  GitHubClient.RepoMeta meta, String sha, Path dir,
                                  String entryFile, int fileCount, long extractedSize,
                                  Map<String, String> frontmatter,
                                  boolean websitePackage) {}

    public Mono<ImportPreview> preview(String url) {
        GitHubClient.SourceRef source = GitHubClient.parseSourceUrl(url);
        return fetch(source)
            .map(pkg -> {
                ImportPreview preview = toPreview(pkg);
                cleanupQuietly(pkg.dir());
                return preview;
            });
    }

    public Mono<SkillVo> importSkill(String url, String slug, String displayName,
                                     String category, String summary, List<String> tags,
                                     Boolean published) {
        GitHubClient.SourceRef source = GitHubClient.parseSourceUrl(url);
        return fetch(source)
            .flatMap(pkg -> Mono.fromCallable(
                    () -> persistImport(pkg, slug, displayName, category, summary, tags, published))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::createSkill));
    }

    public Mono<UpstreamStatus> checkUpstream(String name) {
        return client.fetch(Skill.class, name)
            .switchIfEmpty(Mono.error(new ServerWebInputException("Skill not found: " + name)))
            .flatMap(skill -> {
                GitHubClient.SourceRef source = sourceOf(skill);
                return github.fetchHeadCommit(source)
                    .map(head -> {
                        String current = skill.getSpec().getUpstreamCommit();
                        boolean hasUpdate = current == null || !current.equals(head);
                        String version = skill.getSpec().getUpstreamVersion();
                        String msg = hasUpdate
                            ? "上游有新版本（" + shortSha(head) + "）"
                            : "已是最新（" + shortSha(head) + "）";
                        return new UpstreamStatus(hasUpdate, current, head, version, msg);
                    });
            });
    }

    public Mono<SkillVo> syncNow(String name) {
        return client.fetch(Skill.class, name)
            .switchIfEmpty(Mono.error(new ServerWebInputException("Skill not found: " + name)))
            .flatMap(skill -> {
                if (!"github".equals(skill.getSpec().getSourceType())) {
                    return Mono.error(new ServerWebInputException(
                        "仅 GitHub 来源的技能支持同步：" + name));
                }
                GitHubClient.SourceRef source = sourceOf(skill);
                return fetch(source)
                    .flatMap(pkg -> Mono.fromCallable(() -> replaceContent(skill, pkg))
                        .subscribeOn(Schedulers.boundedElastic()))
                    .flatMap(client::update)
                    .flatMap(s -> skillService.getByName(name));
            });
    }

    // ---------- internals ----------

    private GitHubClient.SourceRef sourceOf(Skill skill) {
        String repo = skill.getSpec().getSourceRepo();
        if (repo == null || !repo.contains("/")) {
            throw new ServerWebInputException("技能缺少 GitHub 来源信息：" + repo);
        }
        String[] parts = repo.split("/", 2);
        return new GitHubClient.SourceRef(parts[0], parts[1],
            skill.getSpec().getSourceRef(), skill.getSpec().getSourceSubdir());
    }

    private Mono<FetchedPackage> fetch(GitHubClient.SourceRef source) {
        return github.fetchRepoMeta(source)
            .flatMap(meta -> github.fetchHeadCommit(source)
                .flatMap(sha -> github.listFiles(source, sha)
                    .flatMap(files -> downloadFiles(source, sha, files))
                    .flatMap(dir -> Mono.fromCallable(
                            () -> analyzePackage(source, meta, sha, dir))
                        .subscribeOn(Schedulers.boundedElastic()))));
    }

    private static final int MAX_FILES = 500;
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final int DOWNLOAD_CONCURRENCY = 6;

    /**
     * Download the (subdir-filtered) file set into a temp directory. Per-file
     * downloads run through raw.githubusercontent.com (fast, no rate limit)
     * with the GitHub contents API as fallback — a monorepo zipball via
     * codeload is unusably slow on hosts with restricted GitHub bandwidth.
     */
    private Mono<Path> downloadFiles(GitHubClient.SourceRef source, String sha,
                                     List<GitHubClient.GhFile> allFiles) {
        List<GitHubClient.GhFile> selected = selectFiles(source, allFiles);
        if (selected.isEmpty()) {
            throw new ServerWebInputException(
                (source.subdir() != null ? "子目录 " + source.subdir() + " 中" : "仓库中")
                    + "没有可导入的文件（隐藏目录已跳过）");
        }
        long total = selected.stream().mapToLong(GitHubClient.GhFile::size).sum();
        if (total > MAX_TOTAL_BYTES) {
            throw new ServerWebInputException("文件总大小超过 64MB 限制");
        }
        return Mono.fromCallable(() -> {
                Path tmp = Files.createTempDirectory("gh-import-");
                for (var f : selected) {
                    Path target = tmp.resolve(f.path()).normalize();
                    if (!target.startsWith(tmp)) {
                        throw new ServerWebInputException("非法文件路径：" + f.path());
                    }
                    Files.createDirectories(target.getParent());
                }
                return tmp;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(tmpDir ->
                Flux.fromIterable(selected)
                    .flatMap(f -> github.fetchFile(source, sha, fullPath(source, f.path()))
                        .flatMap(bytes -> Mono.fromCallable(() -> {
                            if (bytes.length > MAX_FILE_BYTES) {
                                throw new ServerWebInputException(
                                    "文件超过 20MB：" + f.path());
                            }
                            Files.write(tmpDir.resolve(f.path()), bytes);
                            return true;
                        }).subscribeOn(Schedulers.boundedElastic())),
                        DOWNLOAD_CONCURRENCY)
                    .then()
                    .thenReturn(tmpDir)
                    .onErrorResume(e -> {
                        cleanupQuietly(tmpDir);
                        return Mono.error(e);
                    }));
    }

    /** Full repo path of a (subdir-stripped) relative file path. */
    private static String fullPath(GitHubClient.SourceRef source, String relPath) {
        return source.subdir() != null ? source.subdir() + "/" + relPath : relPath;
    }

    /**
     * Keep only files under the configured subdir, strip the subdir prefix,
     * and drop hidden / macOS metadata paths at any depth.
     */
    private static List<GitHubClient.GhFile> selectFiles(GitHubClient.SourceRef source,
                                                         List<GitHubClient.GhFile> all) {
        String prefix = source.subdir() != null ? source.subdir() + "/" : "";
        List<GitHubClient.GhFile> out = new java.util.ArrayList<>();
        for (GitHubClient.GhFile f : all) {
            if (out.size() >= MAX_FILES) {
                throw new ServerWebInputException("文件数超过 500，请使用单技能仓库或子目录");
            }
            if (!f.path().startsWith(prefix)) {
                continue;
            }
            String rel = f.path().substring(prefix.length());
            if (rel.isEmpty()) {
                continue;
            }
            boolean hidden = false;
            for (String seg : rel.split("/")) {
                if (seg.startsWith(".") || seg.equals("__MACOSX")) {
                    hidden = true;
                    break;
                }
            }
            if (!hidden && f.size() <= MAX_FILE_BYTES) {
                out.add(new GitHubClient.GhFile(rel, f.size()));
            }
        }
        return out;
    }

    /**
     * Analyze a downloaded package directory: detect the entry file
     * (SKILL.md / README.md, or index.html for website packages) and parse
     * frontmatter.
     */
    private FetchedPackage analyzePackage(GitHubClient.SourceRef source,
                                          GitHubClient.RepoMeta meta, String sha,
                                          Path dir) {
        try {
            int fileCount;
            long extractedSize;
            try (var walk = Files.walk(dir)) {
                List<Path> files = walk.filter(Files::isRegularFile).toList();
                fileCount = files.size();
                extractedSize = files.stream().mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            }
            if (fileCount == 0) {
                throw new ServerWebInputException("仓库是空的");
            }
            boolean isWebsite = Files.isRegularFile(dir.resolve("index.html"));
            String entryFile = isWebsite ? null : findEntryFile(dir);
            if (!isWebsite && entryFile == null) {
                throw new ServerWebInputException(
                    "仓库（或指定子目录）中未找到技能入口：需要 index.html 或 "
                        + "SKILL.md / README.md；若技能在子目录，请用 "
                        + "github.com/owner/repo/tree/branch/subdir 形式的地址导入");
            }
            Map<String, String> frontmatter = isWebsite ? Map.of()
                : FrontmatterParser.parse(dir.resolve(entryFile));
            return new FetchedPackage(source, meta, sha, dir, entryFile,
                fileCount, extractedSize, frontmatter, isWebsite);
        } catch (RuntimeException e) {
            cleanupQuietly(dir);
            throw e;
        } catch (Exception e) {
            cleanupQuietly(dir);
            throw new ServerWebInputException("解析仓库内容失败：" + e.getMessage());
        }
    }


    /**
     * Move a directory, falling back to a recursive copy when source and target
     * live on different filesystems (/tmp vs the halo data dir).
     */
    private static void moveDir(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst);
            return;
        } catch (IOException ignored) {
            // cross-device or non-empty target — fall through to copy
        }
        try (var walk = Files.walk(src)) {
            for (Path from : (Iterable<Path>) walk::iterator) {
                Path to = dst.resolve(src.relativize(from).toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.createDirectories(to.getParent());
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        storageDelete(src);
    }

    private static void storageDelete(Path p) {
        try (var paths = Files.walk(p)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private ImportPreview toPreview(FetchedPackage pkg) {
        Map<String, String> fm = pkg.frontmatter();
        String repoName = pkg.source().repo();
        String displayName = orDefault(fm.get("name"), repoName);
        return new ImportPreview(
            pkg.source().repoSlug(),
            pkg.source().ref() != null ? pkg.source().ref() : pkg.meta().defaultBranch(),
            pkg.sha(),
            pkg.meta().defaultBranch(),
            suggestSlug(repoName),
            displayName,
            orDefault(fm.get("description"), pkg.meta().description()),
            orDefault(fm.get("version"), "1.0.0"),
            pkg.entryFile(),
            pkg.fileCount(),
            pkg.extractedSize(),
            pkg.meta().ownerLogin(),
            pkg.meta().ownerAvatar(),
            pkg.meta().ownerLogin() != null
                ? "https://github.com/" + pkg.meta().ownerLogin() : null,
            pkg.meta().homepage(),
            pkg.meta().stars(),
            pkg.websitePackage());
    }

    private Skill persistImport(FetchedPackage pkg, String slug, String displayName,
                                String category, String summary, List<String> tags,
                                Boolean published) {
        Map<String, String> fm = pkg.frontmatter();
        String repoName = pkg.source().repo();

        String finalSlug = slug != null && !slug.isBlank() ? slug.trim().toLowerCase(Locale.ROOT)
            : suggestSlug(repoName);
        if (!SkillService.isValidSlug(finalSlug)) {
            throw new ServerWebInputException("无效的 slug：" + finalSlug);
        }
        Path skillDir = storage.skillDir(finalSlug);
        if (Files.exists(skillDir)) {
            throw new ServerWebInputException("slug 已存在：" + finalSlug);
        }
        try {
            Files.createDirectories(skillDir.getParent());
            moveDir(pkg.dir(), skillDir);
        } catch (IOException e) {
            throw new ServerWebInputException("保存技能文件失败：" + e.getMessage());
        }

        Skill skill = new Skill();
        skill.getMetadata().setName(finalSlug);
        Skill.Spec spec = new Skill.Spec();
        spec.setDisplayName(orDefault(displayName, orDefault(fm.get("name"), repoName)));
        spec.setCategory(category != null && !category.isBlank() ? category : "default");
        spec.setSummary(clampSummary(
            orDefault(summary, orDefault(fm.get("description"),
                pkg.meta().description()))));
        spec.setTags(tags != null ? tags : List.of());
        spec.setSourceType("github");
        spec.setSourceRepo(pkg.source().repoSlug());
        spec.setSourceSubdir(pkg.source().subdir());
        spec.setSourceRef(pkg.source().ref() != null ? pkg.source().ref()
            : pkg.meta().defaultBranch());
        spec.setUpstreamCommit(pkg.sha());
        String version = orDefault(fm.get("version"), "1.0.0");
        spec.setUpstreamVersion(version);
        spec.setVersion(version);
        spec.setZipPath(skillDir.toString());
        spec.setEntryFile(pkg.entryFile());
        spec.setAuthor(pkg.meta().ownerLogin());
        spec.setAuthorAvatar(pkg.meta().ownerAvatar());
        spec.setAuthorUrl(pkg.meta().ownerLogin() != null
            ? "https://github.com/" + pkg.meta().ownerLogin() : null);
        spec.setPublished(published == null || published);
        spec.setSortOrder(0);
        spec.setDownloads(0L);
        spec.setViews(0L);
        spec.setCreatedAt(Instant.now());
        skill.setSpec(spec);
        return skill;
    }

    private Mono<SkillVo> createSkill(Skill skill) {
        return skillService.create(skill)
            .onErrorResume(e -> {
                if ("github".equals(skill.getSpec().getSourceType())) {
                    storage.deleteQuietly(storage.skillDir(skill.getMetadata().getName()));
                }
                return Mono.error(e);
            });
    }

    /**
     * Swap in the freshly fetched content (validate first, then replace, mirroring
     * SkillService#replaceZip) and refresh upstream metadata on the spec.
     */
    private Skill replaceContent(Skill skill, FetchedPackage pkg) {
        String name = skill.getMetadata().getName();
        Path skillDir = storage.skillDir(name);
        Path tmpDir = storage.skillDir(name + ".tmp-" + Long.toHexString(System.nanoTime()));
        try {
            Files.createDirectories(tmpDir.getParent());
            Files.move(pkg.dir(), tmpDir);
            storage.deleteQuietly(skillDir);
            moveDir(tmpDir, skillDir);
        } catch (IOException e) {
            storage.deleteQuietly(tmpDir);
            throw new ServerWebInputException("替换技能文件失败：" + e.getMessage());
        }

        Skill.Spec spec = skill.getSpec();
        spec.setZipPath(skillDir.toString());
        spec.setEntryFile(pkg.entryFile());
        spec.setUpstreamCommit(pkg.sha());
        String version = orDefault(pkg.frontmatter().get("version"),
            spec.getVersion() != null ? spec.getVersion() : "1.0.0");
        spec.setUpstreamVersion(version);
        spec.setVersion(version);
        // upstream description refreshes the summary; display name stays as curated
        String upstreamSummary = orDefault(pkg.frontmatter().get("description"),
            pkg.meta().description());
        if (upstreamSummary != null) {
            spec.setSummary(clampSummary(upstreamSummary));
        }
        spec.setUpdatedAt(Instant.now());
        return skill;
    }


    /** spec.summary 的 schema 上限是 500 字符，frontmatter 描述可能更长。 */
    private static String clampSummary(String s) {
        if (s == null || s.length() <= 490) {
            return s;
        }
        return s.substring(0, 487) + "...";
    }

    private static String orDefault(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static String suggestSlug(String repoName) {
        String slug = repoName.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (slug.isEmpty() || !SkillService.isValidSlug(slug)) {
            slug = "skill-" + Long.toHexString(System.currentTimeMillis());
        }
        return slug;
    }

    private static String shortSha(String sha) {
        return sha != null && sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private static void cleanupQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        storageDelete(dir);
    }

    private static String findEntryFile(Path root) {
        for (var candidate : List.of("SKILL.md", "skill.md", "README.md", "readme.md",
                "Readme.md", "README", "readme")) {
            if (Files.isRegularFile(root.resolve(candidate))) {
                return candidate;
            }
        }
        try (var entries = Files.list(root)) {
            return entries
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".md"))
                .sorted(String::compareToIgnoreCase)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}

package run.halo.skillshowcase.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.skillshowcase.dto.SkillCategoryVo;
import run.halo.skillshowcase.dto.SkillFileNode;
import run.halo.skillshowcase.dto.SkillFileVo;
import run.halo.skillshowcase.dto.SkillVo;
import run.halo.skillshowcase.extension.Skill;
import run.halo.skillshowcase.extension.Skill.SkillFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private static final long MAX_ZIP_BYTES = 50L * 1024 * 1024;
    private static final Pattern SLUG_PATTERN =
        Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final ReactiveExtensionClient client;
    private final ZipStorageService storage;
    private final ZipExtractorService zipExtractor;
    private final SkillCategoryService categoryService;

    private volatile Map<String, SkillCategoryVo> categoryCache;

    private Mono<Map<String, SkillCategoryVo>> getCategoryMap() {
        if (categoryCache != null) {
            return Mono.just(categoryCache);
        }
        return categoryService.listAll()
            .map(cats -> {
                Map<String, SkillCategoryVo> map = new ConcurrentHashMap<>();
                cats.forEach(c -> map.put(c.name(), c));
                this.categoryCache = map;
                return map;
            });
    }

    public void invalidateCategoryCache() {
        this.categoryCache = null;
    }

    public Mono<SkillVo> create(Skill skill) {
        if (skill.getMetadata().getName() == null) {
            skill.getMetadata().setName(generateSlug(skill.getSpec().getDisplayName()));
        }
        if (skill.getSpec().getCreatedAt() == null) {
            skill.getSpec().setCreatedAt(Instant.now());
        }
        if (skill.getSpec().getSortOrder() == null) {
            skill.getSpec().setSortOrder(0);
        }
        if (skill.getSpec().getPublished() == null) {
            skill.getSpec().setPublished(true);
        }
        return client.create(skill)
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)));
    }

    public Mono<SkillVo> update(String name, Skill skill) {
        return client.fetch(Skill.class, name)
            .switchIfEmpty(Mono.error(new ServerWebInputException("Skill not found: " + name)))
            .flatMap(existing -> {
                skill.getMetadata().setName(name);
                skill.getMetadata().setVersion(existing.getMetadata().getVersion());
                Skill.Spec incoming = skill.getSpec();
                Skill.Spec old = existing.getSpec();
                // The console editor only edits a subset of fields; preserve the rest
                // (ZIP path / entry file / parsed files, stats, author, version) so an
                // edit does not wipe them out.
                if (incoming.getZipPath() == null) {
                    incoming.setZipPath(old.getZipPath());
                }
                if (incoming.getEntryFile() == null) {
                    incoming.setEntryFile(old.getEntryFile());
                }
                if (incoming.getFiles() == null || incoming.getFiles().isEmpty()) {
                    incoming.setFiles(old.getFiles());
                }
                if (incoming.getVersion() == null) {
                    incoming.setVersion(old.getVersion());
                }
                if (incoming.getAuthor() == null) {
                    incoming.setAuthor(old.getAuthor());
                }
                if (incoming.getAuthorAvatar() == null) {
                    incoming.setAuthorAvatar(old.getAuthorAvatar());
                }
                if (incoming.getAuthorUrl() == null) {
                    incoming.setAuthorUrl(old.getAuthorUrl());
                }
                if (incoming.getDownloads() == null) {
                    incoming.setDownloads(old.getDownloads());
                }
                if (incoming.getRating() == null) {
                    incoming.setRating(old.getRating());
                }
                if (incoming.getRatingCount() == null) {
                    incoming.setRatingCount(old.getRatingCount());
                }
                if (incoming.getFavorites() == null) {
                    incoming.setFavorites(old.getFavorites());
                }
                if (incoming.getInstallCommand() == null) {
                    incoming.setInstallCommand(old.getInstallCommand());
                }
                incoming.setCreatedAt(old.getCreatedAt());
                incoming.setUpdatedAt(Instant.now());
                return client.update(skill);
            })
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)));
    }

    public Mono<Void> delete(String name) {
        return client.fetch(Skill.class, name)
            .switchIfEmpty(Mono.error(new ServerWebInputException("Skill not found: " + name)))
            .flatMap(skill -> {
                if ("zip".equals(skill.getSpec().getSourceType())) {
                    storage.deleteQuietly(storage.skillDir(name));
                }
                return client.delete(skill);
            })
            .then();
    }

    public Mono<SkillVo> getByName(String name) {
        return client.fetch(Skill.class, name)
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)));
    }

    public Mono<SkillVo> getPublicByName(String name) {
        return client.fetch(Skill.class, name)
            .filter(s -> Boolean.TRUE.equals(s.getSpec().getPublished()))
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)));
    }

    public Mono<List<SkillVo>> listAll() {
        return getCategoryMap().flatMap(catMap ->
            client.listAll(Skill.class, new ListOptions(),
                    Sort.by(Sort.Order.asc("metadata.name")))
                .map(s -> toVo(s, catMap))
                .collectList()
                .map(list -> {
                    list.sort((a, b) -> {
                        int cmp = Integer.compare(a.sortOrder() != null ? a.sortOrder() : 0,
                                b.sortOrder() != null ? b.sortOrder() : 0);
                        if (cmp != 0) return cmp;
                        return a.name().compareTo(b.name());
                    });
                    return list;
                })
        );
    }

    public Mono<List<SkillVo>> listPublished() {
        return getCategoryMap().flatMap(catMap ->
            client.listAll(Skill.class, new ListOptions(),
                    Sort.by(Sort.Order.asc("metadata.name")))
                .filter(s -> Boolean.TRUE.equals(s.getSpec().getPublished()))
                .map(s -> toVo(s, catMap))
                .collectList()
                .map(list -> {
                    list.sort((a, b) -> {
                        int cmp = Integer.compare(a.sortOrder() != null ? a.sortOrder() : 0,
                                b.sortOrder() != null ? b.sortOrder() : 0);
                        if (cmp != 0) return cmp;
                        return a.name().compareTo(b.name());
                    });
                    return list;
                })
        );
    }

    public Mono<List<SkillVo>> listPublishedByCategory(String category) {
        return getCategoryMap().flatMap(catMap ->
            client.listAll(Skill.class, new ListOptions(),
                    Sort.by(Sort.Order.asc("metadata.name")))
                .filter(s -> Boolean.TRUE.equals(s.getSpec().getPublished()))
                .filter(s -> category.equals(s.getSpec().getCategory()))
                .map(s -> toVo(s, catMap))
                .collectList()
                .map(list -> {
                    list.sort((a, b) -> {
                        int cmp = Integer.compare(a.sortOrder() != null ? a.sortOrder() : 0,
                                b.sortOrder() != null ? b.sortOrder() : 0);
                        if (cmp != 0) return cmp;
                        return a.name().compareTo(b.name());
                    });
                    return list;
                })
        );
    }

    public Mono<SkillVo> uploadZip(FilePart file, String displayName, String slug,
                                    String category, String summary, String coverImage,
                                    List<String> tags) {
        String rawSlug = normalizeSlug(slug);
        final String normalizedSlug = (rawSlug != null) ? rawSlug : generateSlug(displayName);
        if (!isValidSlug(normalizedSlug)) {
            return Mono.error(new ServerWebInputException("Invalid slug: " + normalizedSlug));
        }
        return client.fetch(Skill.class, normalizedSlug)
            .flatMap(existing -> Mono.<SkillVo>error(
                new ServerWebInputException("Skill already exists: " + normalizedSlug)))
            .switchIfEmpty(DataBufferUtils.join(file.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes;
                    try (var is = dataBuffer.asInputStream()) {
                        bytes = is.readAllBytes();
                    } catch (IOException e) {
                        DataBufferUtils.release(dataBuffer);
                        return Mono.error(e);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                    if (bytes.length > MAX_ZIP_BYTES) {
                        return Mono.error(new ServerWebInputException("zip cannot exceed 50MB"));
                    }
                    final String fn = file.filename();
                    return Mono.fromCallable(() -> prepareZip(bytes, normalizedSlug,
                            displayName, category, summary, coverImage, tags, fn))
                        .flatMap(this::save);
                }));
    }

    private Skill prepareZip(byte[] bytes, String slug, String displayName,
                              String category, String summary, String coverImage,
                              List<String> tags, String originalFilename) {
        Path skillDir = storage.skillDir(slug);
        if (Files.exists(skillDir)) {
            throw new ServerWebInputException("slug already exists: " + slug);
        }
        try {
            Files.createDirectories(skillDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ZipExtractorService.Result result;
        try (var bais = new java.io.ByteArrayInputStream(bytes)) {
            result = zipExtractor.extract(bais, skillDir);
        } catch (Exception e) {
            storage.deleteQuietly(skillDir);
            throw new ServerWebInputException("extract failed: " + e.getMessage());
        }
        if (result.fileCount() == 0) {
            storage.deleteQuietly(skillDir);
            throw new ServerWebInputException("empty zip");
        }
        zipExtractor.flattenSingleWrapperDir(skillDir);
        // Two kinds of ZIP are supported:
        //  1) website package : contains index.html, previewed as a website (entryFile == null).
        //  2) skillhub package : markdown-based skill (SKILL.md / README.md as default entry),
        //     no index.html required; browsed with a VSCode-style file tree.
        boolean isWebsite = Files.isRegularFile(skillDir.resolve("index.html"));
        String entryFile = isWebsite ? null : findEntryFile(skillDir);
        if (!isWebsite && entryFile == null) {
            storage.deleteQuietly(skillDir);
            throw new ServerWebInputException(
                "zip must contain an index.html (website package) or a markdown entry "
                    + "(SKILL.md / README.md) in its root or single top-level directory");
        }
        Skill skill = new Skill();
        skill.getMetadata().setName(slug);
        skill.setSpec(new Skill.Spec());
        skill.getSpec().setDisplayName(
            (displayName == null || displayName.isBlank()) ? stripExt(originalFilename) : displayName);
        skill.getSpec().setCategory(category != null ? category : "default");
        skill.getSpec().setSummary(summary);
        skill.getSpec().setCoverImage(coverImage);
        skill.getSpec().setTags(tags != null ? tags : List.of());
        skill.getSpec().setSourceType("zip");
        skill.getSpec().setZipPath(skillDir.toString());
        skill.getSpec().setEntryFile(entryFile);
        skill.getSpec().setPublished(true);
        skill.getSpec().setSortOrder(0);
        skill.getSpec().setCreatedAt(Instant.now());
        return skill;
    }

    private Mono<SkillVo> save(Skill skill) {
        return client.create(skill)
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)))
            .onErrorResume(e -> {
                if ("zip".equals(skill.getSpec().getSourceType())) {
                    storage.deleteQuietly(storage.skillDir(skill.getMetadata().getName()));
                }
                return Mono.error(new RuntimeException("create skill failed: " + e.getMessage(), e));
            });
    }

    public Path skillDir(String name) {
        return storage.skillDir(name);
    }

    private SkillVo toVo(Skill s, Map<String, SkillCategoryVo> catMap) {
        String previewUrl = null;
        if ("zip".equals(s.getSpec().getSourceType())) {
            previewUrl = "/showcase/" + s.getMetadata().getName() + "/";
        } else if ("external".equals(s.getSpec().getSourceType())) {
            previewUrl = s.getSpec().getExternalUrl();
        } else {
            previewUrl = "/showcase/" + s.getMetadata().getName();
        }
        String categoryName = "";
        String categoryIcon = "";
        String categoryColor = "";
        if (s.getSpec().getCategory() != null) {
            categoryName = s.getSpec().getCategory();
            SkillCategoryVo cat = catMap.get(categoryName);
            if (cat != null) {
                categoryName = cat.displayName() != null ? cat.displayName() : categoryName;
                categoryIcon = cat.icon() != null ? cat.icon() : "";
                categoryColor = cat.color() != null ? cat.color() : "";
            }
        }
        List<SkillFileVo> files = List.of();
        if (s.getSpec().getFiles() != null) {
            files = s.getSpec().getFiles().stream()
                .map(f -> new SkillFileVo(
                    f.getPath(),
                    f.getName(),
                    f.getLanguage(),
                    f.getContent(),
                    f.getSize()
                ))
                .collect(Collectors.toList());
        }
        return new SkillVo(
            s.getMetadata().getName(),
            s.getMetadata().getVersion(),
            s.getSpec().getDisplayName(),
            s.getSpec().getCategory(),
            categoryName,
            categoryIcon,
            categoryColor,
            s.getSpec().getCoverImage(),
            s.getSpec().getSummary(),
            s.getSpec().getContent(),
            s.getSpec().getTags(),
            s.getSpec().getSourceType(),
            previewUrl,
            s.getSpec().getExternalUrl(),
            s.getSpec().getEntryFile(),
            s.getSpec().getSortOrder(),
            s.getSpec().getPublished(),
            s.getSpec().getCreatedAt(),
            s.getSpec().getUpdatedAt(),
            files,
            s.getSpec().getVersion() != null ? s.getSpec().getVersion() : "1.0.0",
            s.getSpec().getAuthor(),
            s.getSpec().getAuthorAvatar(),
            s.getSpec().getAuthorUrl(),
            s.getSpec().getDownloads() != null ? s.getSpec().getDownloads() : 0L,
            s.getSpec().getRating() != null ? s.getSpec().getRating() : 0.0,
            s.getSpec().getRatingCount() != null ? s.getSpec().getRatingCount() : 0L,
            s.getSpec().getFavorites() != null ? s.getSpec().getFavorites() : 0L,
            s.getSpec().getInstallCommand()
        );
    }

    public Mono<SkillVo> incrementDownloads(String name) {
        return client.fetch(Skill.class, name)
            .switchIfEmpty(Mono.error(new ServerWebInputException("Skill not found: " + name)))
            .flatMap(skill -> {
                Long downloads = skill.getSpec().getDownloads();
                skill.getSpec().setDownloads((downloads != null ? downloads : 0L) + 1);
                skill.getSpec().setUpdatedAt(Instant.now());
                return client.update(skill);
            })
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)));
    }

    public Mono<SkillVo> incrementFavorites(String name) {
        return client.fetch(Skill.class, name)
            .switchIfEmpty(Mono.error(new ServerWebInputException("Skill not found: " + name)))
            .flatMap(skill -> {
                Long favs = skill.getSpec().getFavorites();
                skill.getSpec().setFavorites((favs != null ? favs : 0L) + 1);
                skill.getSpec().setUpdatedAt(Instant.now());
                return client.update(skill);
            })
            .flatMap(s -> getCategoryMap().map(catMap -> toVo(s, catMap)));
    }

    public Mono<Map<String, Object>> getStats() {
        return client.listAll(Skill.class, new ListOptions(), Sort.unsorted())
            .collectList()
            .map(skills -> {
                long total = skills.size();
                long published = skills.stream().filter(s -> Boolean.TRUE.equals(s.getSpec().getPublished())).count();
                long totalDownloads = skills.stream()
                    .mapToLong(s -> s.getSpec().getDownloads() != null ? s.getSpec().getDownloads() : 0L).sum();
                long totalFavorites = skills.stream()
                    .mapToLong(s -> s.getSpec().getFavorites() != null ? s.getSpec().getFavorites() : 0L).sum();
                Map<String, Object> stats = new HashMap<>();
                stats.put("totalSkills", total);
                stats.put("publishedSkills", published);
                stats.put("totalDownloads", totalDownloads);
                stats.put("totalFavorites", totalFavorites);
                stats.put("totalCategories", skills.stream()
                    .map(s -> s.getSpec().getCategory()).filter(Objects::nonNull).distinct().count());
                return stats;
            });
    }

    public Mono<List<SkillVo>> getRelatedSkills(String name, int limit) {
        return client.fetch(Skill.class, name)
            .flatMap(target -> {
                String category = target.getSpec().getCategory();
                if (category == null) {
                    return Mono.just(List.of());
                }
                return getCategoryMap().flatMap(catMap ->
                    client.listAll(Skill.class, new ListOptions(), Sort.unsorted())
                        .filter(s -> Boolean.TRUE.equals(s.getSpec().getPublished()))
                        .filter(s -> !s.getMetadata().getName().equals(name))
                        .filter(s -> category.equals(s.getSpec().getCategory()))
                        .map(s -> toVo(s, catMap))
                        .collectList()
                        .map(list -> {
                            list.sort((a, b) -> Long.compare(
                                b.downloads() != null ? b.downloads() : 0L,
                                a.downloads() != null ? a.downloads() : 0L
                            ));
                            if (list.size() > limit) {
                                return list.subList(0, limit);
                            }
                            return list;
                        })
                );
            });
    }

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG_PATTERN.matcher(slug).matches();
    }

    private static String normalizeSlug(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (!isValidSlug(trimmed)) return null;
        return trimmed;
    }

    private static String generateSlug(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "skill-" + System.currentTimeMillis();
        }
        String slug = displayName.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (slug.isEmpty()) {
            return "skill-" + System.currentTimeMillis();
        }
        if (slug.length() > 63) {
            slug = slug.substring(0, 63);
        }
        if (!isValidSlug(slug)) {
            return "skill-" + System.currentTimeMillis();
        }
        return slug;
    }

    private static String stripExt(String name) {
        if (name == null) return "未命名";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Detect the default entry file of a skillhub-style package at the skill root.
     * Prefers SKILL.md, then README.md/README, then the first .md file (alphabetical).
     *
     * @return relative file name at the root, or null if no markdown entry is found
     */
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

    /**
     * Build a VSCode-style nested file tree of a skill package from disk.
     * Hidden files/folders and macOS metadata are skipped (unless they are the root markdown docs).
     */
    public List<SkillFileNode> buildFileTree(String name) {
        Path root = storage.skillDir(name);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        return listTree(root, root, "");
    }

    private static boolean isHiddenOrMeta(Path p) {
        String n = p.getFileName().toString();
        return n.startsWith(".") || n.equals("__MACOSX");
    }

    private static List<SkillFileNode> listTree(Path root, Path dir, String prefix) {
        List<SkillFileNode> nodes = new java.util.ArrayList<>();
        java.util.List<java.util.Map.Entry<Boolean, Path>> sorted = new java.util.ArrayList<>();
        try (var entries = Files.list(dir)) {
            entries.filter(p -> !isHiddenOrMeta(p)).forEach(p -> {
                sorted.add(java.util.Map.entry(Files.isDirectory(p), p));
            });
        } catch (IOException e) {
            return nodes;
        }
        // directories first, then files, each alphabetically
        // (Boolean.compare(false, true) < 0 so dirs(true) come before files(false))
        sorted.sort((a, b) -> {
            int cmp = Boolean.compare(b.getKey(), a.getKey());
            if (cmp != 0) return cmp;
            return a.getValue().getFileName().toString().compareToIgnoreCase(
                    b.getValue().getFileName().toString());
        });
        for (var kv : sorted) {
            Path p = kv.getValue();
            Path rel = root.relativize(p);
            String pathStr = rel.toString();
            String nameStr = p.getFileName().toString();
            if (Files.isDirectory(p)) {
                List<SkillFileNode> children = listTree(root, p, prefix);
                nodes.add(new SkillFileNode(pathStr, nameStr, "dir", null, null, children));
            } else {
                String lang = ZipExtractorService.guessLanguageToLang(nameStr);
                long size;
                try {
                    size = Files.size(p);
                } catch (IOException e) {
                    size = -1;
                }
                nodes.add(new SkillFileNode(pathStr, nameStr, "file", lang, size,
                        null));
            }
        }
        return nodes;
    }

    /**
     * Re-package the extracted skill directory back into a single zip (for download).
     * Hidden files and macOS metadata are skipped.
     */
    public byte[] zipContent(String name) {
        Path root = storage.skillDir(name);
        if (!Files.isDirectory(root)) {
            throw new ServerWebInputException("skill storage not found: " + name);
        }
        try (var bos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(bos)) {
            zipDir(zos, root, root, "");
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("failed to archive skill: " + name, e);
        }
    }

    private static void zipDir(java.util.zip.ZipOutputStream zos, Path root, Path dir, String prefix)
            throws IOException {
        try (var entries = Files.list(dir)) {
            var list = entries.filter(p -> !isHiddenOrMeta(p)).sorted(
                (a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString())).toList();
            for (Path p : list) {
                String entryName = prefix + p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    zipDir(zos, root, p, entryName + "/");
                } else {
                    zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                    Files.copy(p, zos);
                    zos.closeEntry();
                }
            }
        }
    }
}
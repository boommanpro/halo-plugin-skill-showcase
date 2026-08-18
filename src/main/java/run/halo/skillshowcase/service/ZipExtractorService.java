package run.halo.skillshowcase.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;

@Component
public class ZipExtractorService {

    public static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;

    public record Result(long extractedSize, int fileCount) {}

    public Result extract(InputStream in, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        long extracted = 0;
        int count = 0;
        try (var zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (!isSafeEntryName(name)) {
                    throw new ServerWebInputException("illegal zip entry: " + name);
                }
                long entrySize = entry.getSize();
                if (entrySize > MAX_ENTRY_SIZE) {
                    throw new ServerWebInputException("zip entry too large: " + name);
                }
                Path target = targetDir.resolve(name).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new ServerWebInputException("zip entry escapes target dir: " + name);
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                long written = Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                extracted += written;
                count++;
                if (count > 5000) {
                    throw new ServerWebInputException("too many files (>5000)");
                }
                if (extracted > 200L * 1024 * 1024) {
                    throw new ServerWebInputException("extracted size exceeds 200MB");
                }
                zis.closeEntry();
            }
        }
        return new Result(extracted, count);
    }

    public static boolean isSafeEntryName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.indexOf('\0') >= 0) return false;
        if (name.contains("..")) return false;
        if (name.startsWith("/") || name.startsWith("\\")) return false;
        if (name.contains("\\")) return false;
        return true;
    }

    public static String sanitizeForUrlPath(String rest) {
        if (rest == null) return "";
        if (rest.indexOf('\0') >= 0 || rest.contains("..") || rest.contains("\\")) return null;
        if (rest.startsWith("/")) rest = rest.substring(1);
        return rest;
    }

    /**
     * Map a file name to a code-language label (for file-tree badges), or an empty string
     * for unknown extensions.
     */
    public static String guessLanguageToLang(String filename) {
        if (filename == null) return "";
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            ext = filename.substring(dot + 1).toLowerCase();
        }
        return switch (ext) {
            case "md", "markdown", "pdc" -> "markdown";
            case "java" -> "java";
            case "js", "mjs", "cjs" -> "javascript";
            case "ts", "tsx", "mts" -> "typescript";
            case "jsx" -> "javascript";
            case "py", "pyw" -> "python";
            case "html", "htm", "vue", "svelte", "xhtml" -> "html";
            case "css", "scss", "less" -> "css";
            case "json", "jsonc" -> "json";
            case "xml" -> "xml";
            case "yml", "yaml" -> "yaml";
            case "sh", "bash", "zsh", "fish" -> "shell";
            case "sql" -> "sql";
            case "go" -> "go";
            case "rs" -> "rust";
            case "rb" -> "ruby";
            case "php" -> "php";
            case "c" -> "c";
            case "h" -> "c";
            case "cpp", "cc", "cxx", "hpp" -> "cpp";
            case "cs" -> "csharp";
            case "gradle", "properties", "toml", "ini", "cfg", "conf" -> "config";
            case "txt", "log" -> "text";
            default -> "";
        };
    }

    public static String guessContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            ext = filename.substring(dot + 1).toLowerCase();
        }
        return switch (ext) {
            case "html", "htm" -> "text/html;charset=utf-8";
            case "css" -> "text/css;charset=utf-8";
            case "js", "mjs" -> "application/javascript;charset=utf-8";
            case "json" -> "application/json;charset=utf-8";
            case "xml" -> "application/xml;charset=utf-8";
            case "txt", "md" -> "text/plain;charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }

    public void flattenSingleWrapperDir(Path projectDir) {
        if (Files.exists(projectDir.resolve("index.html"))) return;
        Path wrapper = null;
        try (var entries = Files.list(projectDir)) {
            var visible = entries.filter(p -> {
                String name = p.getFileName().toString();
                return !name.startsWith(".") && !name.equals("__MACOSX");
            }).toList();
            if (visible.size() == 1 && Files.isDirectory(visible.get(0))) {
                wrapper = visible.get(0);
            }
        } catch (IOException ignored) {
            return;
        }
        if (wrapper == null) return;
        if (!Files.exists(wrapper.resolve("index.html")) && !hasMarkdownEntry(wrapper)) return;
        try {
            try (var entries = Files.list(wrapper)) {
                for (var p : (Iterable<Path>) entries::iterator) {
                    Path dst = projectDir.resolve(p.getFileName().toString());
                    Files.move(p, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.deleteIfExists(wrapper);
        } catch (IOException ignored) {}
    }

    private static boolean hasMarkdownEntry(Path dir) {
        for (var candidate : new String[]{"SKILL.md", "skill.md", "README.md", "readme.md", "README"}) {
            if (Files.isRegularFile(dir.resolve(candidate))) {
                return true;
            }
        }
        return false;
    }
}
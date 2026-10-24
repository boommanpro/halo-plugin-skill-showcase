package run.halo.skillshowcase.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal parser for the SKILL.md YAML frontmatter (agentskills.io open standard).
 *
 * <p>Only top-level scalar keys are extracted ({@code name}, {@code description},
 * {@code version}, ...), which is all the importer needs. Nested structures and
 * lists are skipped on purpose — pulling in a full YAML dependency for a handful
 * of scalar keys is not worth it.
 */
public final class FrontmatterParser {

    private static final int MAX_PARSE_BYTES = 2 * 1024 * 1024;

    private FrontmatterParser() {
    }

    /**
     * Parse frontmatter of a SKILL.md-like markdown file.
     *
     * @return key-value map of top-level scalar keys; empty map when the file has
     *         no frontmatter (no leading {@code ---} block)
     */
    public static Map<String, String> parse(Path markdownFile) throws IOException {
        if (markdownFile == null || !Files.isRegularFile(markdownFile)) {
            return Map.of();
        }
        long size = Files.size(markdownFile);
        if (size > MAX_PARSE_BYTES) {
            return Map.of();
        }
        List<String> lines = Files.readAllLines(markdownFile, StandardCharsets.UTF_8);
        return parseLines(lines);
    }

    /**
     * Parse frontmatter from raw markdown text.
     */
    public static Map<String, String> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Map.of();
        }
        return parseLines(markdown.lines().toList());
    }

    private static Map<String, String> parseLines(List<String> lines) {
        Map<String, String> result = new HashMap<>();
        int i = 0;
        // skip leading blank lines / BOM noise
        while (i < lines.size() && lines.get(i).isBlank()) {
            i++;
        }
        if (i >= lines.size() || !lines.get(i).trim().equals("---")) {
            return result;
        }
        i++;
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.equals("---") || trimmed.equals("...")) {
                break;
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // skip indented / nested lines and block-scalar values — top-level only
            if (line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = stripQuotes(trimmed.substring(colon + 1).trim());
            if (!key.isEmpty() && !value.isEmpty() && !"-|>".contains(value)) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

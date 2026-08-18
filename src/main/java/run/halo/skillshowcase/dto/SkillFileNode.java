package run.halo.skillshowcase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Represents a node in a skill package's file tree (VSCode-style).
 *
 * @param path     relative path from skill root, e.g. "references/api-design.md"
 * @param name     base name, e.g. "api-design.md"
 * @param type     "file" or "dir"
 * @param language guessed language label for files (empty for dirs)
 * @param size     file size in bytes (null for dirs)
 * @param children child nodes (populated for dirs)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkillFileNode(
        String path,
        String name,
        String type,
        String language,
        Long size,
        List<SkillFileNode> children
) {}
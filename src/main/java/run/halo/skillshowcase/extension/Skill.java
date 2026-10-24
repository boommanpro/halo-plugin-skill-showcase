package run.halo.skillshowcase.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import run.halo.app.extension.Metadata;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = "skill-showcase.halo.run",
     version = "v1alpha1",
     kind = "Skill",
     plural = "skills",
     singular = "skill")
public class Skill extends AbstractExtension {

    public Skill() {
        setMetadata(new Metadata());
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    public static class Spec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        private String displayName;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String category;

        @Schema
        private String coverImage;

        @Schema(maxLength = 500)
        private String summary;

        @Schema
        private String content;

        @Schema
        private List<String> tags = new ArrayList<>();

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String sourceType;

        /**
         * GitHub source in "owner/repo" form; present when sourceType == "github".
         * Drives npx-style install command generation and upstream sync.
         */
        @Schema(maxLength = 200)
        private String sourceRepo;

        /**
         * Subdirectory inside the repository when the skill is not at repo root.
         */
        @Schema(maxLength = 200)
        private String sourceSubdir;

        /**
         * Branch / tag / ref used at import time; null means the repo default branch.
         */
        @Schema(maxLength = 100)
        private String sourceRef;

        /**
         * Head commit sha captured at the last import/sync, used for update detection.
         */
        @Schema(maxLength = 40)
        private String upstreamCommit;

        /**
         * Upstream version parsed from SKILL.md frontmatter at the last sync.
         */
        @Schema(maxLength = 50)
        private String upstreamVersion;

        @Schema
        private String zipPath;

        /**
         * skillhub-style skill package default entry file, e.g. "SKILL.md" or "README.md".
         * null means this is a website zip (has index.html).
         */
        @Schema
        private String entryFile;

        @Schema
        private String externalUrl;

        @Schema
        private Integer sortOrder = 0;

        @Schema
        private Boolean published = true;

        @Schema
        private Instant createdAt;

        @Schema
        private Instant updatedAt;

        @Schema
        private List<SkillFile> files = new ArrayList<>();

        @Schema(maxLength = 50)
        private String version = "1.0.0";

        @Schema(maxLength = 50)
        private String author;

        @Schema
        private String authorAvatar;

        @Schema
        private String authorUrl;

        @Schema
        private Long downloads = 0L;

        @Schema
        private Long views = 0L;

        @Schema
        private Double rating = 0.0;

        @Schema
        private Long ratingCount = 0L;

        @Schema
        private Long favorites = 0L;

        @Schema
        private String installCommand;
    }

    @Data
    public static class SkillFile {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String path;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema
        private String language;

        @Schema
        private String content;

        @Schema
        private Long size;
    }
}
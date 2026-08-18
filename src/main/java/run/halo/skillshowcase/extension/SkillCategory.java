package run.halo.skillshowcase.extension;

import io.swagger.v3.oas.annotations.media.Schema;
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
     kind = "SkillCategory",
     plural = "skillcategories",
     singular = "skillcategory")
public class SkillCategory extends AbstractExtension {

    public SkillCategory() {
        setMetadata(new Metadata());
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    public static class Spec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
        private String displayName;

        @Schema
        private String description;

        @Schema
        private String icon;

        @Schema
        private String color;

        @Schema
        private Integer sortOrder = 0;
    }
}
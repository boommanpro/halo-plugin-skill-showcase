package run.halo.skillshowcase.dto;

public record SkillCategoryVo(
        String name,
        String displayName,
        String description,
        String icon,
        String color,
        Integer sortOrder
) {}
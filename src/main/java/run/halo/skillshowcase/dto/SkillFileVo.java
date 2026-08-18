package run.halo.skillshowcase.dto;

public record SkillFileVo(
        String path,
        String name,
        String language,
        String content,
        Long size
) {}

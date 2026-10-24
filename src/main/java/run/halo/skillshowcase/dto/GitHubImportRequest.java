package run.halo.skillshowcase.dto;

import java.util.List;

/** Request body of the console GitHub import endpoints. */
public record GitHubImportRequest(
        String url,
        String slug,
        String displayName,
        String category,
        String summary,
        List<String> tags,
        Boolean published) {}

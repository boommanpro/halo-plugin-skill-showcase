package run.halo.skillshowcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebInputException;
import run.halo.skillshowcase.service.GitHubClient.SourceRef;

class GitHubClientSourceRefTest {

    @Test
    void parsesFullHttpsUrl() {
        SourceRef ref = GitHubClient.parseSourceUrl("https://github.com/anthropics/skills");
        assertThat(ref.owner()).isEqualTo("anthropics");
        assertThat(ref.repo()).isEqualTo("skills");
        assertThat(ref.ref()).isNull();
        assertThat(ref.subdir()).isNull();
    }

    @Test
    void parsesTreeUrlWithRefAndSubdir() {
        SourceRef ref = GitHubClient.parseSourceUrl(
            "https://github.com/anthropics/skills/tree/main/document-skills/docx");
        assertThat(ref.repo()).isEqualTo("skills");
        assertThat(ref.ref()).isEqualTo("main");
        assertThat(ref.subdir()).isEqualTo("document-skills/docx");
    }

    @Test
    void parsesShortHandAndSubdirQuery() {
        SourceRef ref = GitHubClient.parseSourceUrl("owner/repo?subdir=skills/pdf");
        assertThat(ref.owner()).isEqualTo("owner");
        assertThat(ref.repo()).isEqualTo("repo");
        assertThat(ref.subdir()).isEqualTo("skills/pdf");

        SourceRef plain = GitHubClient.parseSourceUrl("owner/repo");
        assertThat(plain.subdir()).isNull();
    }

    @Test
    void stripsDotGitAndTrailingSlash() {
        SourceRef ref = GitHubClient.parseSourceUrl("https://github.com/owner/repo.git/");
        assertThat(ref.repo()).isEqualTo("repo");
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> GitHubClient.parseSourceUrl(""))
            .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> GitHubClient.parseSourceUrl("https://gitlab.com/a/b"))
            .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> GitHubClient.parseSourceUrl("not a url at all"))
            .isInstanceOf(ServerWebInputException.class);
    }
}

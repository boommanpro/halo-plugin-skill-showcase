package run.halo.skillshowcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebInputException;

class FrontmatterParserTest {

    @Test
    void parsesStandardSkillFrontmatter() {
        String md = """
            ---
            name: pdf
            description: "Create PDF reports from documents"
            version: 1.2.0
            license: MIT
            ---

            # PDF Skill

            body text
            """;
        Map<String, String> fm = FrontmatterParser.parse(md);
        assertThat(fm.get("name")).isEqualTo("pdf");
        assertThat(fm.get("description")).isEqualTo("Create PDF reports from documents");
        assertThat(fm.get("version")).isEqualTo("1.2.0");
        assertThat(fm.get("license")).isEqualTo("MIT");
    }

    @Test
    void handlesNoFrontmatterAndNestedKeys() {
        assertThat(FrontmatterParser.parse("# Just markdown\nbody")).isEmpty();

        String withNested = """
            ---
            name: top
            allowed-tools:
              - Bash
              - Read
            metadata:
              key: value
            ---
            body
            """;
        Map<String, String> fm = FrontmatterParser.parse(withNested);
        assertThat(fm.get("name")).isEqualTo("top");
        assertThat(fm).doesNotContainKey("allowed-tools");
        assertThat(fm).doesNotContainKey("metadata");
    }

    @Test
    void skipsCommentsAndBlankLines() {
        String md = """
            ---
            # a comment
            name: x

            description: 'single quoted'
            ---
            """;
        Map<String, String> fm = FrontmatterParser.parse(md);
        assertThat(fm.get("name")).isEqualTo("x");
        assertThat(fm.get("description")).isEqualTo("single quoted");
        assertThat(fm).doesNotContainKey("# a comment");
    }
}

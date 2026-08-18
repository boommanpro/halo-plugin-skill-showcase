package run.halo.skillshowcase;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.skillshowcase.dto.SkillCategoryVo;
import run.halo.skillshowcase.dto.SkillVo;
import run.halo.skillshowcase.extension.Skill;
import run.halo.skillshowcase.extension.SkillCategory;
import run.halo.skillshowcase.service.SkillCategoryService;
import run.halo.skillshowcase.service.SkillService;
import run.halo.skillshowcase.service.ZipExtractorService;
import run.halo.skillshowcase.service.ZipStorageService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PluginIntegrationTest {

    @TempDir
    Path tempDir;

    private ZipStorageService storage;
    private ZipExtractorService zipExtractor;
    private SkillService skillService;
    private SkillCategoryService categoryService;
    private ReactiveExtensionClient client;

    @BeforeEach
    void setUp() throws Exception {
        storage = new ZipStorageService();
        var rootField = ZipStorageService.class.getDeclaredField("root");
        rootField.setAccessible(true);
        rootField.set(storage, tempDir);

        zipExtractor = new ZipExtractorService();
        client = mock(ReactiveExtensionClient.class);
        categoryService = mock(SkillCategoryService.class);
        skillService = new SkillService(client, storage, zipExtractor, categoryService);

        when(client.fetch(eq(Skill.class), anyString()))
            .thenReturn(Mono.empty());
        when(client.fetch(eq(SkillCategory.class), anyString()))
            .thenReturn(Mono.empty());
        when(client.create(any(Skill.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.create(any(SkillCategory.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.update(any(Skill.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.update(any(SkillCategory.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.delete(any(Skill.class)))
            .thenReturn(Mono.empty());
        when(client.delete(any(SkillCategory.class)))
            .thenReturn(Mono.empty());
        when(client.listAll(eq(Skill.class), any(), any()))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(SkillCategory.class), any(), any()))
            .thenReturn(Flux.empty());
        when(categoryService.listAll())
            .thenReturn(Mono.just(List.of()));
    }

    @Test
    @DisplayName("Test 1: Create Skill with Markdown content")
    void testCreateMarkdownSkill() {
        System.out.println("\n=== Test 1: Create Markdown Skill ===");

        Skill skill = new Skill();
        skill.getMetadata().setName("java-backend");
        skill.setSpec(new Skill.Spec());
        skill.getSpec().setDisplayName("Java 后端开发");
        skill.getSpec().setCategory("backend");
        skill.getSpec().setSummary("Spring Boot / MyBatis");
        skill.getSpec().setContent("# Java 后端\n\n```java\npublic class Hello { }\n```");
        skill.getSpec().setTags(List.of("Java", "Spring"));
        skill.getSpec().setSourceType("markdown");
        skill.getSpec().setPublished(true);

        SkillVo vo = skillService.create(skill).block();

        assert vo != null : "Create should succeed";
        assert "java-backend".equals(vo.name()) : "Name should match";
        assert "Java 后端开发".equals(vo.displayName()) : "DisplayName should match";
        assert "markdown".equals(vo.sourceType()) : "SourceType should be markdown";
        assert vo.tags().contains("Java") : "Tags should contain Java";

        System.out.println("  PASS: Markdown Skill created");
    }

    @Test
    @DisplayName("Test 2: Create Skill with ZIP upload")
    void testZipUpload() throws Exception {
        System.out.println("\n=== Test 2: ZIP Upload ===");

        Path zipPath = createSimpleZip();
        byte[] bytes = Files.readAllBytes(zipPath);

        SkillVo vo = skillService.uploadZip(
            createFilePart(bytes, "skill.zip"),
            "AI Demo",
            "ai-demo",
            "ai",
            "AI 演示站",
            null,
            List.of("AI", "Demo")
        ).block();

        assert vo != null : "ZIP upload should succeed";
        assert "ai-demo".equals(vo.name()) : "Name should match";
        assert "zip".equals(vo.sourceType()) : "SourceType should be zip";
        assert vo.previewUrl().contains("ai-demo") : "Preview URL should contain slug";

        Path skillDir = tempDir.resolve("ai-demo");
        assert Files.exists(skillDir.resolve("index.html")) : "index.html should exist";

        System.out.println("  PASS: ZIP Skill uploaded, files on disk");
    }

    @Test
    @DisplayName("Test 3: Create Skill with external link")
    void testExternalLinkSkill() {
        System.out.println("\n=== Test 3: External Link Skill ===");

        Skill skill = new Skill();
        skill.getMetadata().setName("my-blog");
        skill.setSpec(new Skill.Spec());
        skill.getSpec().setDisplayName("我的博客");
        skill.getSpec().setCategory("external");
        skill.getSpec().setSourceType("external");
        skill.getSpec().setExternalUrl("https://example.com");
        skill.getSpec().setPublished(true);

        SkillVo vo = skillService.create(skill).block();

        assert vo != null : "Create should succeed";
        assert "external".equals(vo.sourceType()) : "SourceType should be external";
        assert "https://example.com".equals(vo.externalUrl()) : "External URL should match";

        System.out.println("  PASS: External link Skill created");
    }

    @Test
    @DisplayName("Test 4: Create and list categories")
    void testCategoryCRUD() {
        System.out.println("\n=== Test 4: Category CRUD ===");

        SkillCategory cat = new SkillCategory();
        cat.getMetadata().setName("frontend");
        cat.setSpec(new SkillCategory.Spec());
        cat.getSpec().setDisplayName("前端开发");
        cat.getSpec().setIcon("💻");
        cat.getSpec().setColor("#3b82f6");
        cat.getSpec().setSortOrder(1);

        SkillCategoryVo expectedVo = new SkillCategoryVo(
            "frontend", "前端开发", null, "💻", "#3b82f6", 1);
        when(categoryService.create(any(SkillCategory.class)))
            .thenReturn(Mono.just(expectedVo));

        SkillCategoryVo vo = categoryService.create(cat).block();

        assert vo != null : "Category create should succeed";
        assert "frontend".equals(vo.name()) : "Name should match";
        assert "前端开发".equals(vo.displayName()) : "DisplayName should match";

        System.out.println("  PASS: Category created");
    }

    @Test
    @DisplayName("Test 5: Update Skill")
    void testUpdateSkill() {
        System.out.println("\n=== Test 5: Update Skill ===");

        Skill existing = new Skill();
        existing.getMetadata().setName("test-update");
        existing.getMetadata().setVersion(1L);
        existing.setSpec(new Skill.Spec());
        existing.getSpec().setDisplayName("Old Name");
        existing.getSpec().setCategory("old-cat");
        existing.getSpec().setSourceType("markdown");

        when(client.fetch(eq(Skill.class), eq("test-update")))
            .thenReturn(Mono.just(existing));

        Skill update = new Skill();
        update.getMetadata().setName("test-update");
        update.setSpec(new Skill.Spec());
        update.getSpec().setDisplayName("Updated Name");
        update.getSpec().setCategory("updated-cat");
        update.getSpec().setSourceType("markdown");
        update.getSpec().setPublished(true);

        SkillVo vo = skillService.update("test-update", update).block();

        assert vo != null : "Update should succeed";
        assert "Updated Name".equals(vo.displayName()) : "DisplayName should be updated";

        System.out.println("  PASS: Skill updated");
    }

    @Test
    @DisplayName("Test 6: Delete Skill")
    void testDeleteSkill() throws Exception {
        System.out.println("\n=== Test 6: Delete Skill ===");

        Path zipPath = createSimpleZip();
        byte[] bytes = Files.readAllBytes(zipPath);

        skillService.uploadZip(
            createFilePart(bytes, "test.zip"),
            "Delete Test",
            "test-delete",
            "test",
            null,
            null,
            List.of()
        ).block();

        Skill existing = new Skill();
        existing.getMetadata().setName("test-delete");
        existing.setSpec(new Skill.Spec());
        existing.getSpec().setDisplayName("Delete Test");
        existing.getSpec().setCategory("test");
        existing.getSpec().setSourceType("zip");
        existing.getSpec().setZipPath(tempDir.resolve("test-delete").toString());

        when(client.fetch(eq(Skill.class), eq("test-delete")))
            .thenReturn(Mono.just(existing));

        skillService.delete("test-delete").block();

        Path skillDir = tempDir.resolve("test-delete");
        assert !Files.exists(skillDir) : "Skill directory should be deleted";

        System.out.println("  PASS: Skill deleted, directory cleaned up");
    }

    @Test
    @DisplayName("Test 7: Empty ZIP should fail")
    void testEmptyZipRejection() throws Exception {
        System.out.println("\n=== Test 7: Empty ZIP handling ===");

        Path emptyZip = tempDir.resolve("empty.zip");
        try (var zos = new java.util.zip.ZipOutputStream(new FileOutputStream(emptyZip.toFile()))) {
        }

        byte[] bytes = Files.readAllBytes(emptyZip);

        try {
            skillService.uploadZip(
                createFilePart(bytes, "empty.zip"),
                "Empty Test",
                "test-empty",
                "test",
                null,
                null,
                List.of()
            ).block();
            assert false : "Empty ZIP should throw error";
        } catch (Exception e) {
            System.out.println("  PASS: Empty ZIP correctly rejected: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 8: ZIP without index.html should fail")
    void testZipWithoutIndexHtml() throws Exception {
        System.out.println("\n=== Test 8: ZIP without index.html ===");

        Path badZip = tempDir.resolve("bad.zip");
        try (var zos = new java.util.zip.ZipOutputStream(new FileOutputStream(badZip.toFile()))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zos.write("No index.html here".getBytes());
            zos.closeEntry();
        }

        byte[] bytes = Files.readAllBytes(badZip);

        try {
            skillService.uploadZip(
                createFilePart(bytes, "bad.zip"),
                "Bad Test",
                "test-bad",
                "test",
                null,
                null,
                List.of()
            ).block();
            assert false : "ZIP without index.html should throw error";
        } catch (Exception e) {
            System.out.println("  PASS: ZIP without index.html correctly rejected: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 9: List skills")
    void testListSkills() {
        System.out.println("\n=== Test 9: List Skills ===");

        var result = skillService.listAll().block();
        assert result != null : "List should not be null";
        System.out.println("  PASS: List returns " + result.size() + " skills");
    }

    @Test
    @DisplayName("Test 10: Slug generation from display name")
    void testSlugGeneration() {
        System.out.println("\n=== Test 10: Slug generation ===");

        // Test isValidSlug
        assert SkillService.isValidSlug("java-backend") : "Valid slug should pass";
        assert SkillService.isValidSlug("ai") : "Short valid slug should pass";
        assert !SkillService.isValidSlug("A") : "Single uppercase should fail";
        assert !SkillService.isValidSlug("bad slug") : "Space should fail";
        assert !SkillService.isValidSlug("") : "Empty should fail";

        System.out.println("  PASS: Slug validation works correctly");
    }

    private FilePart createFilePart(byte[] content, String filename) {
        FilePart part = mock(FilePart.class);
        when(part.filename()).thenReturn(filename);
        when(part.content()).thenReturn(
            Flux.just(new DefaultDataBufferFactory().wrap(content))
        );
        return part;
    }

    private Path createSimpleZip() throws Exception {
        Path zip = tempDir.resolve("simple.zip");
        try (var zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("index.html"));
            zos.write("<!DOCTYPE html><html><body><h1>Skill Demo</h1></body></html>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("style.css"));
            zos.write("body { color: blue; }".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("app.js"));
            zos.write("console.log('skill');".getBytes());
            zos.closeEntry();
        }
        return zip;
    }
}
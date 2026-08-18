package run.halo.skillshowcase.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ZipStorageService {

    @Value("${halo.plugin.skill-showcase.storage-root:#{'${user.home}/.halo2/skill-showcase'}}")
    private String storageRoot;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(storageRoot);
        Files.createDirectories(root);
        log.info("[skill-showcase] storage root = {}", root.toAbsolutePath());
    }

    public Path root() {
        return root;
    }

    public Path skillDir(String slug) {
        return root.resolve(slug);
    }

    public void deleteQuietly(Path p) {
        if (p == null || !Files.exists(p)) return;
        try {
            try (var paths = Files.walk(p)) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
            }
        } catch (IOException e) {
            log.warn("[skill-showcase] failed to delete {}: {}", p, e.getMessage());
        }
    }
}
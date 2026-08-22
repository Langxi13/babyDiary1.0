package com.langxi.babydiary.transfer.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ExportTempCleaner {
    private static final Logger log = LoggerFactory.getLogger(ExportTempCleaner.class);
    private static final List<String> PREFIXES =
            List.of(
                    "baby-diary-v3-export-",
                    "baby-diary-v3-manifest-",
                    "baby-diary-v3-book-",
                    "baby-diary-images-");

    @EventListener(ApplicationReadyEvent.class)
    public void cleanup() {
        Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        int deleted = 0;
        try (var entries = Files.list(temporaryDirectory)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName().toString();
                if (PREFIXES.stream().noneMatch(name::startsWith)
                        || !Files.isRegularFile(path)
                        || !Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                    continue;
                }
                if (Files.deleteIfExists(path)) deleted++;
            }
        } catch (IOException exception) {
            log.warn("Unable to clean stale export temporary files", exception);
        }
        if (deleted > 0) log.info("Cleaned {} stale export temporary files", deleted);
    }
}

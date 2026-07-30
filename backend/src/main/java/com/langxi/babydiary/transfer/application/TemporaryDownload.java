package com.langxi.babydiary.transfer.application;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;

public final class TemporaryDownload extends FileSystemResource {
    private final Path path;

    public TemporaryDownload(Path path) {
        super(path);
        this.path = path;
        path.toFile().deleteOnExit();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FilterInputStream(super.getInputStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    Files.deleteIfExists(path);
                }
            }
        };
    }
}

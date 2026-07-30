package com.langxi.babydiary.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(@Value("${app.storage.local-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public void put(String key, InputStream input, long size, String contentType)
            throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (size >= 0 && Files.size(temporary) != size)
                throw new IOException("Object size mismatch");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public StoredObject get(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) throw new IOException("Object not found");
        return new StoredObject(
                Files.newInputStream(path), Files.size(path), Files.probeContentType(path));
    }

    @Override
    public StoredObjectInfo stat(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) throw new IOException("Object not found");
        return new StoredObjectInfo(Files.size(path), Files.probeContentType(path));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public void verifyReady() throws IOException {
        Files.createDirectories(root);
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IOException("Local object storage is not writable");
        }
    }

    private Path resolve(String key) {
        Path value = root.resolve(key).normalize();
        if (!value.startsWith(root))
            throw new IllegalArgumentException("Object key escapes storage root");
        return value;
    }
}

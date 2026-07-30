package com.langxi.babydiary.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorage {
    String provider();

    void put(String key, InputStream input, long size, String contentType) throws IOException;

    StoredObject get(String key) throws IOException;

    default StoredObject get(String key, long offset, long length) throws IOException {
        StoredObject object = get(key);
        try {
            object.stream().skipNBytes(offset);
            return new StoredObject(
                    new LimitedInputStream(object.stream(), length), length, object.contentType());
        } catch (IOException | RuntimeException exception) {
            object.close();
            throw exception;
        }
    }

    StoredObjectInfo stat(String key) throws IOException;

    void delete(String key) throws IOException;

    default void verifyReady() throws IOException {}
}

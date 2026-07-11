package com.langxi.babydiary.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorage {
    String provider();

    void put(String key, InputStream input, long size, String contentType) throws IOException;

    StoredObject get(String key) throws IOException;

    void delete(String key) throws IOException;
}

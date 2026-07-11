package com.langxi.babydiary.storage;

import java.io.InputStream;

public record StoredObject(InputStream stream, long length, String contentType) implements AutoCloseable {
    @Override
    public void close() throws java.io.IOException {
        stream.close();
    }
}

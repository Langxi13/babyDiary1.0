package com.langxi.babydiary.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class LimitedInputStream extends FilterInputStream {
    private long remaining;

    LimitedInputStream(InputStream input, long remaining) {
        super(input);
        this.remaining = Math.max(0, remaining);
    }

    @Override
    public int read() throws IOException {
        if (remaining == 0) return -1;
        int value = super.read();
        if (value >= 0) remaining--;
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (remaining == 0) return -1;
        int read = super.read(bytes, offset, (int) Math.min(length, remaining));
        if (read > 0) remaining -= read;
        return read;
    }
}

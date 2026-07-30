package com.langxi.babydiary.media.api;

public final class MediaRangeException extends RuntimeException {
    private final long total;

    MediaRangeException(long total) {
        super("Requested range is not satisfiable");
        this.total = total;
    }

    public long total() {
        return total;
    }
}

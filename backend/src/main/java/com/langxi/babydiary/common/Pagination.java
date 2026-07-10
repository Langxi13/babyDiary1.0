package com.langxi.babydiary.common;

public final class Pagination {

    public static final int MAX_PAGE_SIZE = 100;

    private Pagination() {
    }

    public static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    public static int normalizeSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    public static long offset(int page, int size) {
        return (long) normalizePage(page) * normalizeSize(size);
    }
}

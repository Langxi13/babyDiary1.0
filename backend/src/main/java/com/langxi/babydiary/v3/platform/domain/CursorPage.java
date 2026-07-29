package com.langxi.babydiary.v3.platform.domain;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, long totalElements) {
    public CursorPage {
        items = List.copyOf(items);
    }
}

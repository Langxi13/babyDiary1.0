package com.langxi.babydiary.platform.domain;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, Long totalElements) {
    public CursorPage {
        items = List.copyOf(items);
    }
}

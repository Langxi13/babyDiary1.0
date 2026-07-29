package com.langxi.babydiary.v3.diary.application;

public interface DiaryContentPolicy {
    Content normalize(String html);

    record Content(String html, String text) {
    }
}

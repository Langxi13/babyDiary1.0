package com.langxi.babydiary.diary.application;

public interface DiaryContentPolicy {
    Content normalize(String html);

    record Content(String html, String text) {
    }
}

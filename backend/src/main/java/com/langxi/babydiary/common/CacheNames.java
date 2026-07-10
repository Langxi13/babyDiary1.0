package com.langxi.babydiary.common;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CacheNames {

    public static final String DIARY_PAGE = "diaryPage";
    public static final String DIARY_TIMELINE = "diaryTimeline";
    public static final String DIARY_CALENDAR = "diaryCalendar";
    public static final String TAGS = "tags";
    public static final String ANNIVERSARIES = "anniversaries";
    public static final String PHOTOS = "photos";
    public static final String DRAFTS = "drafts";

    private CacheNames() {
    }

    public static Map<String, Duration> ttlByCacheName() {
        Map<String, Duration> ttl = new LinkedHashMap<>();
        ttl.put(DIARY_PAGE, Duration.ofSeconds(30));
        ttl.put(DIARY_TIMELINE, Duration.ofMinutes(2));
        ttl.put(DIARY_CALENDAR, Duration.ofMinutes(2));
        ttl.put(TAGS, Duration.ofMinutes(10));
        ttl.put(ANNIVERSARIES, Duration.ofMinutes(10));
        ttl.put(PHOTOS, Duration.ofSeconds(30));
        ttl.put(DRAFTS, Duration.ofSeconds(30));
        return Collections.unmodifiableMap(ttl);
    }
}

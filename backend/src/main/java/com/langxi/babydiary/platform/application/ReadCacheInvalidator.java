package com.langxi.babydiary.platform.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ReadCacheInvalidator {
    public static final String TAGS = "tags";
    public static final String DIARY_AGGREGATES = "diary-aggregates";
    public static final String ALBUM_METADATA = "album-metadata";
    public static final String HOME = "home";

    private final ReadCache cache;

    public ReadCacheInvalidator(ReadCache cache) {
        this.cache = cache;
    }

    public void diary(UUID spaceId) {
        afterCommit(spaceId, Set.of(DIARY_AGGREGATES, HOME));
    }

    public void tags(UUID spaceId) {
        afterCommit(spaceId, Set.of(TAGS, HOME));
    }

    public void albums(UUID spaceId) {
        afterCommit(spaceId, Set.of(ALBUM_METADATA, HOME));
    }

    public void diaryAndAlbums(UUID spaceId) {
        afterCommit(spaceId, Set.of(DIARY_AGGREGATES, ALBUM_METADATA, HOME));
    }

    public void home(UUID spaceId) {
        afterCommit(spaceId, Set.of(HOME));
    }

    private void afterCommit(UUID spaceId, Set<String> areas) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            areas.forEach(area -> cache.invalidate(area, spaceId));
                        }
                    });
            return;
        }
        areas.forEach(area -> cache.invalidate(area, spaceId));
    }
}

package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

public interface ReadCache {
    <T> T get(
            String area,
            UUID spaceId,
            long accountId,
            String variant,
            Duration ttl,
            TypeReference<T> type,
            Supplier<T> loader);

    void invalidate(String area, UUID spaceId);
}

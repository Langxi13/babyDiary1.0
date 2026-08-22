package com.langxi.babydiary.space.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpaceAccessProjectionCache {
    private final SpaceGateway spaces;
    private final MeterRegistry metrics;
    private final Cache<Key, Projection> cache;

    public SpaceAccessProjectionCache(
            SpaceGateway spaces,
            MeterRegistry metrics,
            @Value("${app.access-cache.space.maximum-size:8192}") long maximumSize,
            @Value("${app.access-cache.space.expire-after-access:120s}") Duration expiry) {
        this.spaces = spaces;
        this.metrics = metrics;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(Math.max(1, maximumSize))
                        .expireAfterAccess(
                                expiry.isNegative() || expiry.isZero()
                                        ? Duration.ofSeconds(1)
                                        : expiry)
                        .build();
    }

    public Optional<SpaceAccess.SpaceContext> find(UUID spaceId, long accountId) {
        Key key = new Key(spaceId, accountId);
        Projection cached = cache.getIfPresent(key);
        if (cached != null) {
            event("hit");
            return Optional.of(cached.context());
        }
        event("miss");
        Optional<Projection> loaded = spaces.findContext(spaceId, accountId).map(Projection::from);
        loaded.ifPresent(value -> cache.put(key, value));
        return loaded.map(Projection::context);
    }

    public void invalidate(UUID spaceId, long accountId) {
        cache.invalidate(new Key(spaceId, accountId));
        event("invalidate");
    }

    public void invalidateSpace(UUID spaceId) {
        cache.asMap().keySet().removeIf(key -> key.spaceId().equals(spaceId));
        event("invalidate");
    }

    private void event(String result) {
        metrics.counter(
                        "baby.diary.cache.requests",
                        "cache",
                        "space",
                        "area",
                        "membership",
                        "result",
                        result)
                .increment();
    }

    private record Key(UUID spaceId, long accountId) {}

    private record Projection(
            long internalId,
            UUID publicId,
            String role,
            String type,
            String defaultVisibility,
            long storageQuotaBytes) {
        private static Projection from(SpaceAccess.SpaceContext context) {
            return new Projection(
                    context.internalId(),
                    context.publicId(),
                    context.role(),
                    context.type(),
                    context.defaultVisibility(),
                    context.storageQuotaBytes());
        }

        private SpaceAccess.SpaceContext context() {
            return new SpaceAccess.SpaceContext(
                    internalId, publicId, role, type, defaultVisibility, storageQuotaBytes, 0);
        }
    }
}

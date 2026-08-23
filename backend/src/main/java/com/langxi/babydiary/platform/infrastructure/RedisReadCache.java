package com.langxi.babydiary.platform.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.langxi.babydiary.platform.application.ReadCache;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisReadCache implements ReadCache {
    private static final Logger log = LoggerFactory.getLogger(RedisReadCache.class);
    private static final Duration VERSION_TTL = Duration.ofDays(30);
    private static final int FALLBACK_MAX_BYTES = 8 * 1024 * 1024;

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String prefix;
    private final AtomicLong nextWarningAt = new AtomicLong();
    private final AtomicLong disabledUntil = new AtomicLong();
    private final Set<Invalidation> pendingInvalidations = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CompletableFuture<Object>> loads =
            new ConcurrentHashMap<>();
    private final Cache<String, String> fallback;
    private final MeterRegistry metrics;
    private final DistributionSummary payloadBytes;

    public RedisReadCache(
            ObjectProvider<StringRedisTemplate> redis,
            ObjectMapper json,
            MeterRegistry metrics,
            @Value("${app.cache.enabled:true}") boolean enabled,
            @Value("${app.cache.prefix:baby-diary:cache:}") String prefix) {
        this.redis = redis.getIfAvailable();
        this.json = json;
        this.metrics = metrics;
        this.payloadBytes =
                DistributionSummary.builder("baby.diary.cache.payload.bytes")
                        .description("Serialized Redis read-cache payload size")
                        .register(metrics);
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.isBlank() ? "baby-diary:cache:" : prefix;
        this.fallback =
                Caffeine.<String, String>newBuilder()
                        .maximumWeight(FALLBACK_MAX_BYTES)
                        .weigher(
                                (String key, String value) ->
                                        Math.min(
                                                FALLBACK_MAX_BYTES,
                                                key.getBytes(StandardCharsets.UTF_8).length
                                                        + value.getBytes(StandardCharsets.UTF_8)
                                                                .length))
                        .expireAfterWrite(Duration.ofSeconds(30))
                        .build();
    }

    @Override
    public <T> T get(
            String area,
            UUID spaceId,
            long accountId,
            String variant,
            Duration ttl,
            TypeReference<T> type,
            Supplier<T> loader) {
        String fallbackKey = dataKey(area, spaceId, accountId, "fallback", variant);
        if (!enabled) {
            event(area, "disabled");
            return singleFlight(fallbackKey, loader);
        }
        if (!available()) {
            event(area, "fallback");
            return fallback(fallbackKey, area, type, loader);
        }
        String version;
        String key;
        try {
            flushInvalidations();
            version = version(area, spaceId);
            key = dataKey(area, spaceId, accountId, version, variant);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                event(area, "hit");
                payloadBytes.record(cached.getBytes(StandardCharsets.UTF_8).length);
                fallback.put(fallbackKey, cached);
                return json.readValue(cached, type);
            }
            event(area, "miss");
        } catch (Exception exception) {
            event(area, "fallback");
            warn(exception);
            return fallback(fallbackKey, area, type, loader);
        }

        String resolvedKey = key;
        return singleFlight(
                resolvedKey,
                () -> {
                    T value = loader.get();
                    if (value == null) return null;
                    try {
                        String serialized = json.writeValueAsString(value);
                        redis.opsForValue().set(resolvedKey, serialized, ttl);
                        fallback.put(fallbackKey, serialized);
                        payloadBytes.record(serialized.getBytes(StandardCharsets.UTF_8).length);
                        event(area, "write");
                    } catch (Exception exception) {
                        event(area, "fallback");
                        warn(exception);
                    }
                    return value;
                });
    }

    @Override
    public void invalidate(String area, UUID spaceId) {
        fallback.invalidateAll();
        if (!enabled || redis == null) return;
        if (!available()) {
            pendingInvalidations.add(new Invalidation(area, spaceId));
            return;
        }
        try {
            String key = versionKey(area, spaceId);
            redis.opsForValue().increment(key);
            redis.expire(key, VERSION_TTL);
            event(area, "invalidate");
        } catch (RuntimeException exception) {
            pendingInvalidations.add(new Invalidation(area, spaceId));
            warn(exception);
        }
    }

    private boolean available() {
        return enabled && redis != null && disabledUntil.get() <= Instant.now().getEpochSecond();
    }

    private void flushInvalidations() {
        for (Invalidation invalidation : pendingInvalidations) {
            String key = versionKey(invalidation.area(), invalidation.spaceId());
            redis.opsForValue().increment(key);
            redis.expire(key, VERSION_TTL);
            pendingInvalidations.remove(invalidation);
        }
    }

    private String version(String area, UUID spaceId) {
        String value = redis.opsForValue().get(versionKey(area, spaceId));
        return value == null ? "0" : value;
    }

    private String versionKey(String area, UUID spaceId) {
        return prefix + safe(area) + ":space:" + spaceId + ":version";
    }

    private String dataKey(
            String area, UUID spaceId, long accountId, String version, String variant) {
        return prefix
                + safe(area)
                + ":space:"
                + spaceId
                + ":account:"
                + accountId
                + ":v:"
                + version
                + ":query:"
                + hash(variant);
    }

    private String safe(String value) {
        String normalized = value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_-]", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (value == null ? "" : value)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void warn(Exception exception) {
        long now = Instant.now().getEpochSecond();
        disabledUntil.set(now + 30);
        long next = nextWarningAt.get();
        if (next > now || !nextWarningAt.compareAndSet(next, now + 60)) return;
        log.warn(
                "Redis read cache unavailable; using database fallback: {}",
                exception.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private <T> T singleFlight(String key, Supplier<T> loader) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> active = loads.putIfAbsent(key, mine);
        if (active != null) {
            try {
                return (T) active.join();
            } catch (CompletionException exception) {
                throw propagate(exception.getCause());
            }
        }
        try {
            T value = loader.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            loads.remove(key, mine);
        }
    }

    private <T> T fallback(String key, String area, TypeReference<T> type, Supplier<T> loader) {
        String cached = fallback.getIfPresent(key);
        if (cached != null) {
            try {
                event(area, "local-hit");
                return json.readValue(cached, type);
            } catch (Exception exception) {
                fallback.invalidate(key);
            }
        }
        return singleFlight(
                key,
                () -> {
                    String completed = fallback.getIfPresent(key);
                    if (completed != null) {
                        try {
                            event(area, "local-hit");
                            return json.readValue(completed, type);
                        } catch (Exception exception) {
                            fallback.invalidate(key);
                        }
                    }
                    T value = loader.get();
                    if (value == null) return null;
                    try {
                        String serialized = json.writeValueAsString(value);
                        fallback.put(key, serialized);
                        payloadBytes.record(serialized.getBytes(StandardCharsets.UTF_8).length);
                    } catch (Exception exception) {
                        log.debug("Unable to serialize local read-cache fallback", exception);
                    }
                    return value;
                });
    }

    private RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtime) return runtime;
        if (throwable instanceof Error error) throw error;
        return new IllegalStateException(throwable);
    }

    private void event(String area, String result) {
        metrics.counter(
                        "baby.diary.cache.requests",
                        "cache",
                        "redis",
                        "area",
                        safe(area),
                        "result",
                        result)
                .increment();
    }

    private record Invalidation(String area, UUID spaceId) {}
}

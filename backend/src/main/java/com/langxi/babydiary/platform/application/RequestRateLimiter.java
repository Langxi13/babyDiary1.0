package com.langxi.babydiary.platform.application;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RequestRateLimiter.class);
    private static final DefaultRedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return current
            """,
                    Long.class);
    private static final int MAX_LOCAL_KEYS = 20_000;

    private final ConcurrentHashMap<String, Window> local = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanup = new AtomicLong();
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final boolean redisEnabled;

    public RequestRateLimiter(
            ObjectProvider<StringRedisTemplate> redis,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.redis-enabled:false}") boolean redisEnabled) {
        this.redis = redis.getIfAvailable();
        this.enabled = enabled;
        this.redisEnabled = redisEnabled;
    }

    public void require(String scope, String identity, int limit, long windowSeconds) {
        if (!enabled) return;
        String key = "baby-diary:v3:rate:" + safeScope(scope) + ":" + hash(identity);
        long count = redisCount(key, windowSeconds);
        if (count < 0) count = localCount(key, windowSeconds);
        if (count > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后重试");
        }
    }

    public String client(HttpServletRequest request) {
        String address = request == null ? null : request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    private long redisCount(String key, long windowSeconds) {
        if (!redisEnabled || redis == null) return -1;
        try {
            Long value =
                    redis.execute(INCREMENT, List.of(key), Long.toString(windowSeconds * 1000));
            return value == null ? -1 : value;
        } catch (RuntimeException exception) {
            log.warn(
                    "Redis rate limiter unavailable; using local fallback: {}",
                    exception.getMessage());
            return -1;
        }
    }

    private long localCount(String key, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        Window value =
                local.compute(
                        key,
                        (ignored, current) ->
                                current == null || current.expiresAt <= now
                                        ? new Window(now + windowSeconds, 1)
                                        : new Window(current.expiresAt, current.count + 1));
        cleanup(now);
        return value.count;
    }

    private void cleanup(long now) {
        if (local.size() < MAX_LOCAL_KEYS && nextCleanup.get() > now) return;
        if (!nextCleanup.compareAndSet(nextCleanup.get(), now + 300)) return;
        local.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (local.size() > MAX_LOCAL_KEYS) {
            local.keySet().stream()
                    .limit(local.size() - MAX_LOCAL_KEYS)
                    .toList()
                    .forEach(local::remove);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safeScope(String value) {
        String scope = value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_-]", "");
        return scope.isBlank() ? "unknown" : scope;
    }

    private record Window(long expiresAt, long count) {}
}

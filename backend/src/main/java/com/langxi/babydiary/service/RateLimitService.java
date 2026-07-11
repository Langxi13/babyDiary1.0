package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public String clientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        try {
            if (InetAddress.getByName(remote).isLoopbackAddress()) {
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    String candidate = forwarded.split(",", 2)[0].trim();
                    if (candidate.length() <= 64) return InetAddress.getByName(candidate).getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // Fall back to the connection address when proxy metadata is malformed.
        }
        return remote == null ? "unknown" : remote;
    }

    public void require(String scope, String identity, int limit, Duration duration) {
        if (windows.size() >= 100_000) {
            cleanup();
            if (windows.size() >= 100_000) throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        String key = scope + ":" + SecureTokens.sha256(identity == null ? "unknown" : identity);
        Instant now = Instant.now();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt)) {
                return new Window(1, now.plus(duration));
            }
            return new Window(current.count + 1, current.expiresAt);
        });
        if (window.count > limit) throw new BusinessException(ErrorCode.RATE_LIMITED);
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
    }

    private record Window(int count, Instant expiresAt) { }
}

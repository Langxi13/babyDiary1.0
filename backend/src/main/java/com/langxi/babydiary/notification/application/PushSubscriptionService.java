package com.langxi.babydiary.notification.application;

import com.langxi.babydiary.platform.application.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {
    private final PushSubscriptionRepository subscriptions;
    private final String publicKey;

    public PushSubscriptionService(
            PushSubscriptionRepository subscriptions,
            @Value("${app.push.vapid-public-key:}") String publicKey) {
        this.subscriptions = subscriptions;
        this.publicKey = publicKey;
    }

    public String publicKey() {
        return publicKey == null || publicKey.isBlank() ? null : publicKey;
    }

    @Transactional
    public void subscribe(
            long accountId, String endpoint, String p256dh, String auth, String userAgent) {
        validate(endpoint, p256dh, auth);
        subscriptions.save(
                accountId, hash(endpoint), endpoint, p256dh, auth, truncate(userAgent, 500));
    }

    @Transactional
    public void unsubscribe(long accountId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw ApiException.badRequest("PUSH_ENDPOINT_REQUIRED", "推送地址不能为空");
        }
        subscriptions.revoke(accountId, hash(endpoint));
    }

    private void validate(String endpoint, String p256dh, String auth) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 4096) {
            throw ApiException.badRequest("PUSH_ENDPOINT_INVALID", "推送地址无效");
        }
        if (p256dh == null
                || p256dh.isBlank()
                || p256dh.length() > 255
                || auth == null
                || auth.isBlank()
                || auth.length() > 255) {
            throw ApiException.badRequest("PUSH_KEYS_INVALID", "推送密钥无效");
        }
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }
}

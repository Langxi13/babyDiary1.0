package com.langxi.babydiary.notification.application;

import java.time.LocalDateTime;
import java.util.List;

public interface PushSubscriptionRepository {
    void save(
            long accountId,
            byte[] endpointHash,
            String endpoint,
            String p256dh,
            String authSecret,
            String userAgent);

    void revoke(long accountId, byte[] endpointHash);

    List<Subscription> findActive(long accountId);

    void markSuccess(long subscriptionId, LocalDateTime now);

    void revokeById(long subscriptionId, LocalDateTime now);

    record Subscription(long internalId, String endpoint, String p256dh, String authSecret) {}
}

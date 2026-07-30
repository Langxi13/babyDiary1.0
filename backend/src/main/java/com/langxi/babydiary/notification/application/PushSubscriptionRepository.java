package com.langxi.babydiary.notification.application;

public interface PushSubscriptionRepository {
    void save(
            long accountId,
            byte[] endpointHash,
            String endpoint,
            String p256dh,
            String authSecret,
            String userAgent);

    void revoke(long accountId, byte[] endpointHash);
}

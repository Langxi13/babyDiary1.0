package com.langxi.babydiary.notification.infrastructure;

import com.langxi.babydiary.notification.application.PushSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisPushSubscriptionRepository implements PushSubscriptionRepository {
    private final PushSubscriptionMapper mapper;

    public MyBatisPushSubscriptionRepository(PushSubscriptionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(
            long accountId,
            byte[] endpointHash,
            String endpoint,
            String p256dh,
            String authSecret,
            String userAgent) {
        mapper.upsert(accountId, endpointHash, endpoint, p256dh, authSecret, userAgent);
    }

    @Override
    public void revoke(long accountId, byte[] endpointHash) {
        mapper.revoke(accountId, endpointHash);
    }

    @Override
    public List<Subscription> findActive(long accountId) {
        return mapper.findActive(accountId).stream()
                .map(
                        row ->
                                new Subscription(
                                        row.subscriptionId(),
                                        row.endpoint(),
                                        row.p256dh(),
                                        row.authSecret()))
                .toList();
    }

    @Override
    public void markSuccess(long subscriptionId, LocalDateTime now) {
        mapper.markSuccess(subscriptionId, now);
    }

    @Override
    public void revokeById(long subscriptionId, LocalDateTime now) {
        mapper.revokeById(subscriptionId, now);
    }
}

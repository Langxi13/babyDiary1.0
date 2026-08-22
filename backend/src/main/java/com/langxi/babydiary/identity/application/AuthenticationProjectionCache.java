package com.langxi.babydiary.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.langxi.babydiary.identity.domain.Account;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationProjectionCache {
    private final AccountGateway accounts;
    private final MeterRegistry metrics;
    private final Cache<Key, Projection> cache;

    public AuthenticationProjectionCache(
            AccountGateway accounts,
            MeterRegistry metrics,
            @Value("${app.access-cache.account.maximum-size:2048}") long maximumSize,
            @Value("${app.access-cache.account.expire-after-access:120s}") Duration expiry) {
        this.accounts = accounts;
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

    public Optional<Projection> find(long accountId, int tokenVersion) {
        Key key = new Key(accountId, tokenVersion);
        Projection cached = cache.getIfPresent(key);
        if (cached != null) {
            event("hit");
            return Optional.of(cached);
        }
        event("miss");
        Optional<Projection> loaded =
                accounts.findById(accountId)
                        .filter(Account::active)
                        .filter(account -> account.tokenVersion() == tokenVersion)
                        .map(
                                account ->
                                        new Projection(
                                                account.id(),
                                                account.publicId(),
                                                account.username(),
                                                account.systemRole(),
                                                account.tokenVersion()));
        loaded.ifPresent(value -> cache.put(key, value));
        return loaded;
    }

    public void invalidate(long accountId) {
        cache.asMap().keySet().removeIf(key -> key.accountId() == accountId);
        event("invalidate");
    }

    private void event(String result) {
        metrics.counter(
                        "baby.diary.cache.requests",
                        "cache",
                        "account",
                        "area",
                        "authentication",
                        "result",
                        result)
                .increment();
    }

    private record Key(long accountId, int tokenVersion) {}

    public record Projection(
            long accountId, UUID publicId, String username, String systemRole, int tokenVersion) {}
}

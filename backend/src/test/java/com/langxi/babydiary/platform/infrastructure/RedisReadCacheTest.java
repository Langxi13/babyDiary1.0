package com.langxi.babydiary.platform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.langxi.babydiary.platform.domain.CursorPage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisReadCacheTest {
    private static final TypeReference<CursorPage<SummaryProbe>> VALUE = new TypeReference<>() {};

    @Test
    void boundedLocalFallbackAvoidsRepeatedLoadsAndHonorsInvalidation() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        RedisReadCache cache =
                new RedisReadCache(
                        beans.getBeanProvider(StringRedisTemplate.class),
                        JsonMapper.builder().findAndAddModules().build(),
                        metrics,
                        true,
                        "test:");
        UUID spaceId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();

        CursorPage<SummaryProbe> first =
                cache.get(
                        "home",
                        spaceId,
                        7,
                        "projection",
                        Duration.ofMinutes(2),
                        VALUE,
                        () -> page(loads.incrementAndGet()));
        CursorPage<SummaryProbe> second =
                cache.get(
                        "home",
                        spaceId,
                        7,
                        "projection",
                        Duration.ofMinutes(2),
                        VALUE,
                        () -> page(loads.incrementAndGet()));

        assertThat(first).isEqualTo(page(1));
        assertThat(second).isEqualTo(first);
        assertThat(loads).hasValue(1);

        cache.invalidate("home", spaceId);
        CursorPage<SummaryProbe> afterInvalidation =
                cache.get(
                        "home",
                        spaceId,
                        7,
                        "projection",
                        Duration.ofMinutes(2),
                        VALUE,
                        () -> page(loads.incrementAndGet()));

        assertThat(afterInvalidation).isEqualTo(page(2));
        assertThat(loads).hasValue(2);
        metrics.close();
    }

    private CursorPage<SummaryProbe> page(int load) {
        return new CursorPage<>(
                List.of(new SummaryProbe(LocalDate.of(2026, 8, load), new UUID(0, load))),
                "next-" + load,
                (long) load);
    }

    private record SummaryProbe(LocalDate diaryDate, UUID id) {}
}

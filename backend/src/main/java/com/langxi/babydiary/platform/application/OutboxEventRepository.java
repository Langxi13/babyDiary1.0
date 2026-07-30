package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

public interface OutboxEventRepository {
    int claim(String claimToken, LocalDateTime now);

    Event findClaimed(String claimToken);

    int succeed(long eventId, String claimToken, LocalDateTime now);

    int fail(
            long eventId,
            String claimToken,
            boolean terminal,
            LocalDateTime availableAt,
            String error,
            LocalDateTime now);

    int recoverRetryable(LocalDateTime staleBefore, LocalDateTime now);

    int failExhausted(LocalDateTime staleBefore, LocalDateTime now);

    record Event(
            long eventId,
            UUID id,
            Long spaceInternalId,
            UUID spaceId,
            Long actorId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            JsonNode payload,
            int attemptCount,
            int maxAttempts) {}
}

package com.langxi.babydiary.platform.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.platform.application.OutboxEventRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOutboxEventRepository implements OutboxEventRepository {
    private final OutboxEventMapper mapper;
    private final ObjectMapper json;

    public MyBatisOutboxEventRepository(OutboxEventMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public int claim(String claimToken, LocalDateTime now) {
        return mapper.claim(claimToken, now);
    }

    @Override
    public Event findClaimed(String claimToken) {
        OutboxEventMapper.EventRow row = mapper.findClaimed(claimToken);
        if (row == null) return null;
        try {
            return new Event(
                    row.eventId(),
                    BinaryUuid.fromBytes(row.publicId()),
                    row.spaceId(),
                    row.spacePublicId() == null ? null : BinaryUuid.fromBytes(row.spacePublicId()),
                    row.actorId(),
                    row.aggregateType(),
                    row.aggregatePublicId() == null
                            ? null
                            : BinaryUuid.fromBytes(row.aggregatePublicId()),
                    row.eventType(),
                    json.readTree(row.payload()),
                    row.attemptCount(),
                    row.maxAttempts());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored outbox payload is invalid", exception);
        }
    }

    @Override
    public int succeed(long eventId, String claimToken, LocalDateTime now) {
        return mapper.succeed(eventId, claimToken, now);
    }

    @Override
    public int fail(
            long eventId,
            String claimToken,
            boolean terminal,
            LocalDateTime availableAt,
            String error,
            LocalDateTime now) {
        return mapper.fail(eventId, claimToken, terminal, availableAt, error, now);
    }

    @Override
    public int recoverRetryable(LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.recoverRetryable(staleBefore, now);
    }

    @Override
    public int failExhausted(LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.failExhausted(staleBefore, now);
    }
}

package com.langxi.babydiary.v3.platform.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.platform.application.ChangeRecorder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class MyBatisChangeRecorder implements ChangeRecorder {
    private final OutboxMapper mapper;
    private final ObjectMapper json;
    private final Clock clock = Clock.systemUTC();

    public MyBatisChangeRecorder(OutboxMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public void record(long spaceId, long actorId, String aggregateType, UUID aggregateId,
                       String eventType, int revision, Map<String, Object> payload) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            byte[] binaryId = BinaryUuid.toBytes(aggregateId);
            mapper.insertSync(spaceId, aggregateType, binaryId, revision, actorId, now);
            mapper.insertOutbox(spaceId, aggregateType, binaryId, eventType,
                    json.writeValueAsString(payload == null ? Map.of() : payload), now);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to record V3 change event", exception);
        }
    }
}

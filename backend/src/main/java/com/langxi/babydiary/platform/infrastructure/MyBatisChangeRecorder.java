package com.langxi.babydiary.platform.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.platform.application.ChangeRecorder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

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
    public void record(
            long spaceId,
            long actorId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int revision,
            Scope scope,
            Map<String, Object> payload) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            byte[] binaryId = BinaryUuid.toBytes(aggregateId);
            String operation =
                    eventType != null
                                    && (eventType.endsWith("_DELETED")
                                            || eventType.endsWith("_PURGED"))
                            ? "DELETE"
                            : "UPSERT";
            mapper.insertSync(
                    spaceId,
                    aggregateType,
                    binaryId,
                    operation,
                    revision,
                    scope.visibility(),
                    scope.ownerId(),
                    actorId,
                    now);
            mapper.insertOutbox(
                    spaceId,
                    actorId,
                    aggregateType,
                    binaryId,
                    eventType,
                    json.writeValueAsString(payload == null ? Map.of() : payload),
                    now);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to record V3 change event", exception);
        }
    }
}

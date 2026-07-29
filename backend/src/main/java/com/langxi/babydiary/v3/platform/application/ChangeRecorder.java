package com.langxi.babydiary.v3.platform.application;

import java.util.Map;
import java.util.UUID;

public interface ChangeRecorder {
    void record(long spaceId, long actorId, String aggregateType, UUID aggregateId,
                String eventType, int revision, Map<String, Object> payload);
}

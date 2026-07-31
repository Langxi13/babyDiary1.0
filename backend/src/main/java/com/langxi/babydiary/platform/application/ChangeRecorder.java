package com.langxi.babydiary.platform.application;

import java.util.Map;
import java.util.UUID;

public interface ChangeRecorder {
    void record(
            long spaceId,
            long actorId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int revision,
            Scope scope,
            Map<String, Object> payload);

    record Scope(String visibility, Long ownerId) {
        public Scope {
            if (!"PRIVATE".equals(visibility) && !"SHARED".equals(visibility)) {
                throw new IllegalArgumentException("Change visibility must be PRIVATE or SHARED");
            }
            if ("PRIVATE".equals(visibility) != (ownerId != null)) {
                throw new IllegalArgumentException(
                        "Private changes require an owner and shared changes must not have one");
            }
        }

        public static Scope diary(String visibility, long authorId) {
            if ("PRIVATE".equals(visibility)) return new Scope("PRIVATE", authorId);
            if ("SHARED".equals(visibility)) return new Scope("SHARED", null);
            throw new IllegalArgumentException("Diary visibility must be PRIVATE or SHARED");
        }

        public static Scope shared() {
            return new Scope("SHARED", null);
        }
    }
}

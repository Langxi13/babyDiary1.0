package com.langxi.babydiary.sync.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SyncRepository {
    List<Change> findChanges(long spaceId, long accountId, long cursor, int limit);

    OperationResult findOperation(UUID operationId, long accountId, long spaceId);

    boolean insertOperation(UUID operationId, long accountId, long spaceId, String resultCode,
                            String entityType, UUID entityId, LocalDateTime expiresAt);

    record Change(long cursor, String entityType, UUID entityId, String operation, int revision,
                  long actorId, LocalDateTime createdAt) {
    }

    record OperationResult(String resultCode, String entityType, UUID entityId) {
    }
}

package com.langxi.babydiary.v3.sync.application;

import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.langxi.babydiary.v3.platform.application.V3Exception;

@Service
public class SyncService {
    private final SpaceAccess spaces;
    private final SyncRepository sync;
    private final SyncOperationExecutor executor;

    public SyncService(SpaceAccess spaces, SyncRepository sync, SyncOperationExecutor executor) {
        this.spaces = spaces;
        this.sync = sync;
        this.executor = executor;
    }

    public List<SyncOperationExecutor.Result> push(UUID spaceId, long accountId, List<PushOperation> operations) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (operations == null || operations.isEmpty() || operations.size() > 100) {
            throw V3Exception.badRequest("SYNC_BATCH_INVALID", "同步操作不能为空且单次最多100项");
        }
        return operations.stream().map(item -> executor.execute(spaceId, space.internalId(), accountId,
                new SyncOperationExecutor.Operation(item.operationId(), item.entityType(), item.action(),
                        item.entityId(), item.baseVersion(), item.payload()))).toList();
    }

    public PullResponse pull(UUID spaceId, long accountId, long cursor, int limit) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        int size = Math.max(1, Math.min(limit, 500));
        List<SyncRepository.Change> changes = sync.findChanges(space.internalId(), accountId, Math.max(0, cursor), size);
        long next = changes.isEmpty() ? Math.max(0, cursor) : changes.get(changes.size() - 1).cursor();
        return new PullResponse(changes, next, changes.size() == size);
    }

    public record PullResponse(List<SyncRepository.Change> changes, long nextCursor, boolean hasMore) {
    }

    public record PushOperation(UUID operationId, String entityType, String action, UUID entityId,
                                Integer baseVersion, JsonNode payload) {}
}

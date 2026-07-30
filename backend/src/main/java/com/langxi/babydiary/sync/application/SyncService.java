package com.langxi.babydiary.sync.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

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

    public List<SyncOperationExecutor.Result> push(
            UUID spaceId, long accountId, List<PushOperation> operations, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (operations == null || operations.isEmpty() || operations.size() > 100) {
            throw ApiException.badRequest("SYNC_BATCH_INVALID", "同步操作不能为空且单次最多100项");
        }
        return operations.stream()
                .map(
                        item ->
                                executor.execute(
                                        spaceId,
                                        space.internalId(),
                                        accountId,
                                        elevated,
                                        new SyncOperationExecutor.Operation(
                                                item.operationId(),
                                                item.entityType(),
                                                item.action(),
                                                item.entityId(),
                                                item.baseVersion(),
                                                item.payload())))
                .toList();
    }

    public PullResponse pull(UUID spaceId, long accountId, long cursor, int limit) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        int size = Math.max(1, Math.min(limit, 500));
        long requested = Math.max(0, cursor);
        long baseline = sync.baselineCursor(space.internalId());
        if (requested < baseline) {
            return new PullResponse(List.of(), baseline, false, true, baseline);
        }
        List<SyncRepository.Change> changes =
                sync.findChanges(space.internalId(), accountId, requested, size);
        long next = changes.isEmpty() ? requested : changes.get(changes.size() - 1).cursor();
        return new PullResponse(changes, next, changes.size() == size, false, baseline);
    }

    public record PullResponse(
            List<SyncRepository.Change> changes,
            long nextCursor,
            boolean hasMore,
            boolean resetRequired,
            long baselineCursor) {}

    public record PushOperation(
            UUID operationId,
            String entityType,
            String action,
            UUID entityId,
            Integer baseVersion,
            JsonNode payload) {}
}

package com.langxi.babydiary.sync.infrastructure;

import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.sync.application.SyncRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSyncRepository implements SyncRepository {
    private final SyncMapper mapper;

    public MyBatisSyncRepository(SyncMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Change> findChanges(long spaceId, long accountId, long cursor, int limit) {
        return mapper.findChanges(spaceId, accountId, cursor, limit).stream()
                .map(
                        row ->
                                new Change(
                                        row.changeSeq(),
                                        row.entityType(),
                                        BinaryUuid.fromBytes(row.entityPublicId()),
                                        row.operation(),
                                        row.revision(),
                                        row.actorId(),
                                        row.createdAt()))
                .toList();
    }

    @Override
    public OperationResult findOperation(UUID operationId, long accountId, long spaceId) {
        SyncMapper.OperationRow row =
                mapper.findOperation(BinaryUuid.toBytes(operationId), accountId, spaceId);
        if (row == null) return null;
        return new OperationResult(
                row.getResultCode(),
                row.getEntityType(),
                row.getEntityPublicId() == null
                        ? null
                        : BinaryUuid.fromBytes(row.getEntityPublicId()));
    }

    @Override
    public boolean insertOperation(
            UUID operationId,
            long accountId,
            long spaceId,
            String resultCode,
            String entityType,
            UUID entityId,
            LocalDateTime expiresAt) {
        return mapper.insertOperation(
                        BinaryUuid.toBytes(operationId),
                        accountId,
                        spaceId,
                        resultCode,
                        entityType,
                        entityId == null ? null : BinaryUuid.toBytes(entityId),
                        expiresAt)
                == 1;
    }
}

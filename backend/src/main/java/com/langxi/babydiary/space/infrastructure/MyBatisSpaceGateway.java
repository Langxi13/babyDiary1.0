package com.langxi.babydiary.space.infrastructure;

import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import com.langxi.babydiary.space.application.SpaceGateway;
import com.langxi.babydiary.space.domain.SpaceSummary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisSpaceGateway implements SpaceGateway {
    private final SpaceMapper mapper;

    public MyBatisSpaceGateway(SpaceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SpaceSummary> findForAccount(long accountId) {
        return mapper.findForAccount(accountId).stream().map(row -> new SpaceSummary(
                BinaryUuid.fromBytes(row.publicId()), row.name(), row.type(), row.role(),
                row.defaultVisibility(), row.storageQuotaBytes(), row.storageUsedBytes())).toList();
    }

    @Override
    public Optional<SpaceAccess.SpaceContext> findContext(UUID publicId, long accountId) {
        return Optional.ofNullable(mapper.findContext(BinaryUuid.toBytes(publicId), accountId))
                .map(row -> new SpaceAccess.SpaceContext(row.spaceId(), BinaryUuid.fromBytes(row.publicId()),
                        row.role(), row.type(), row.defaultVisibility(), row.storageQuotaBytes(), row.storageUsedBytes()));
    }

    @Override
    public long insert(UUID publicId, String name, long createdBy, String defaultVisibility, long quotaBytes) {
        SpaceMapper.SpaceInsert row = new SpaceMapper.SpaceInsert(
                BinaryUuid.toBytes(publicId), name, createdBy, defaultVisibility, quotaBytes);
        mapper.insert(row);
        if (row.getSpaceId() == null) throw new IllegalStateException("Space insert returned no ID");
        return row.getSpaceId();
    }

    @Override
    public long insertPersonal(UUID publicId, String name, long ownerId, long quotaBytes) {
        SpaceMapper.SpaceInsert row = new SpaceMapper.SpaceInsert(
                BinaryUuid.toBytes(publicId), name, ownerId, "PRIVATE", quotaBytes);
        mapper.insertPersonal(row);
        if (row.getSpaceId() == null) throw new IllegalStateException("Personal space insert returned no ID");
        return row.getSpaceId();
    }

    @Override
    public void insertOwner(long spaceId, long accountId) {
        mapper.insertOwner(spaceId, accountId);
    }

    @Override
    public void insertStorageUsage(long spaceId) {
        mapper.insertStorageUsage(spaceId);
    }

    @Override
    public boolean update(long spaceId, String name, String defaultVisibility) {
        return mapper.update(spaceId, name, defaultVisibility) == 1;
    }
}

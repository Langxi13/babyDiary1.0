package com.langxi.babydiary.anniversary.infrastructure;

import com.langxi.babydiary.anniversary.application.AnniversaryRepository;
import com.langxi.babydiary.anniversary.domain.Anniversary;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAnniversaryRepository implements AnniversaryRepository {
    private final AnniversaryMapper mapper;

    public MyBatisAnniversaryRepository(AnniversaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Anniversary> findForSpace(long spaceId) {
        return mapper.findForSpace(spaceId).stream().map(this::anniversary).toList();
    }

    @Override
    public Optional<Anniversary> findByPublicId(long spaceId, UUID publicId) {
        return Optional.ofNullable(mapper.findByPublicId(spaceId, BinaryUuid.toBytes(publicId)))
                .map(this::anniversary);
    }

    @Override
    public long insert(NewAnniversary value) {
        AnniversaryMapper.AnniversaryInsert row =
                new AnniversaryMapper.AnniversaryInsert(
                        BinaryUuid.toBytes(value.publicId()),
                        value.spaceId(),
                        value.createdBy(),
                        value.title(),
                        value.date(),
                        value.description(),
                        value.coverAssetId(),
                        value.sortOrder());
        mapper.insert(row);
        if (row.getAnniversaryId() == null)
            throw new IllegalStateException("Anniversary insert returned no ID");
        return row.getAnniversaryId();
    }

    @Override
    public boolean update(long spaceId, UUID publicId, UpdatedAnniversary value) {
        return mapper.update(spaceId, BinaryUuid.toBytes(publicId), value) == 1;
    }

    @Override
    public boolean softDelete(long spaceId, UUID publicId, LocalDateTime deletedAt) {
        return mapper.softDelete(spaceId, BinaryUuid.toBytes(publicId), deletedAt) == 1;
    }

    private Anniversary anniversary(AnniversaryMapper.AnniversaryRow row) {
        return new Anniversary(
                row.anniversaryId(),
                BinaryUuid.fromBytes(row.publicId()),
                BinaryUuid.fromBytes(row.spacePublicId()),
                row.title(),
                row.anniversaryDate(),
                row.description(),
                row.coverPublicId() == null ? null : BinaryUuid.fromBytes(row.coverPublicId()),
                row.sortOrder(),
                row.createdAt(),
                row.updatedAt());
    }
}

package com.langxi.babydiary.v3.draft.infrastructure;

import com.langxi.babydiary.v3.draft.application.DraftRepository;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisDraftRepository implements DraftRepository {
    private final DraftMapper mapper;

    public MyBatisDraftRepository(DraftMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Row> findForOwner(long spaceId, long ownerId) {
        return mapper.findForOwner(spaceId, ownerId).stream().map(this::row).toList();
    }

    @Override
    public Optional<Row> findByKey(long spaceId, long ownerId, String draftKey) {
        return Optional.ofNullable(mapper.findByKey(spaceId, ownerId, draftKey)).map(this::row);
    }

    @Override
    public void upsert(NewDraft draft) {
        mapper.upsert(new DraftMapper.DraftInsert(BinaryUuid.toBytes(draft.publicId()), draft.spaceId(), draft.ownerId(),
                draft.diaryId(), draft.draftKey(), draft.payloadJson()));
    }

    @Override
    public void delete(long spaceId, long ownerId, String draftKey) {
        mapper.delete(spaceId, ownerId, draftKey);
    }

    private Row row(DraftMapper.DraftRow value) {
        return new Row(BinaryUuid.fromBytes(value.publicId()), BinaryUuid.fromBytes(value.spacePublicId()),
                value.draftKey(), value.diaryPublicId() == null ? null : BinaryUuid.fromBytes(value.diaryPublicId()),
                value.payload(), value.createdAt(), value.updatedAt());
    }
}

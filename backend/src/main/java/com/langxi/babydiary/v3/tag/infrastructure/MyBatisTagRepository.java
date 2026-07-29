package com.langxi.babydiary.v3.tag.infrastructure;

import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.tag.application.TagRepository;
import com.langxi.babydiary.v3.tag.domain.Tag;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisTagRepository implements TagRepository {
    private final TagMapper mapper;

    public MyBatisTagRepository(TagMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Tag> findForSpace(long spaceId) {
        return mapper.findForSpace(spaceId).stream().map(this::toTag).toList();
    }

    @Override
    public Optional<Tag> findByPublicId(long spaceId, UUID publicId) {
        return Optional.ofNullable(mapper.findByPublicId(spaceId, BinaryUuid.toBytes(publicId))).map(this::toTag);
    }

    @Override
    public long insert(NewTag tag) {
        TagMapper.TagInsert row = new TagMapper.TagInsert(BinaryUuid.toBytes(tag.publicId()), tag.spaceId(),
                tag.name(), tag.color(), tag.createdBy());
        mapper.insert(row);
        if (row.getTagId() == null) throw new IllegalStateException("Tag insert returned no ID");
        return row.getTagId();
    }

    private Tag toTag(TagMapper.TagRow row) {
        return new Tag(row.tagId(), BinaryUuid.fromBytes(row.publicId()), BinaryUuid.fromBytes(row.spacePublicId()),
                row.name(), row.color());
    }
}

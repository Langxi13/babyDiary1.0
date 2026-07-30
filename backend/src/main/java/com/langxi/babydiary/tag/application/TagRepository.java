package com.langxi.babydiary.tag.application;

import com.langxi.babydiary.tag.domain.Tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository {
    List<Tag> findForSpace(long spaceId);

    Optional<Tag> findByPublicId(long spaceId, UUID publicId);

    long insert(NewTag tag);

    record NewTag(UUID publicId, long spaceId, String name, String color, long createdBy) {
    }
}

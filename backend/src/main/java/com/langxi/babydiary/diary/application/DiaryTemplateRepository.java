package com.langxi.babydiary.diary.application;

import java.time.LocalDateTime;
import java.util.List;

public interface DiaryTemplateRepository {
    List<TemplateData> findAll(long spaceId);

    void insert(NewTemplate template);

    int update(
            long spaceId,
            byte[] publicId,
            long ownerId,
            String name,
            String description,
            String icon,
            String promptText,
            String contentHtml);

    int deactivate(long spaceId, byte[] publicId, long ownerId);

    record NewTemplate(
            byte[] publicId,
            long spaceId,
            long ownerId,
            String name,
            String description,
            String icon,
            String promptText,
            String contentHtml) {}

    record TemplateData(
            byte[] publicId,
            Long ownerId,
            String name,
            String description,
            String icon,
            String promptText,
            String contentHtml,
            boolean builtin,
            LocalDateTime updatedAt) {}
}

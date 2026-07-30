package com.langxi.babydiary.anniversary.application;

import com.langxi.babydiary.anniversary.domain.Anniversary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnniversaryRepository {
    List<Anniversary> findForSpace(long spaceId);

    Optional<Anniversary> findByPublicId(long spaceId, UUID publicId);

    long insert(NewAnniversary anniversary);

    boolean update(long spaceId, UUID publicId, UpdatedAnniversary anniversary);

    boolean softDelete(long spaceId, UUID publicId, LocalDateTime deletedAt);

    record NewAnniversary(
            UUID publicId,
            long spaceId,
            long createdBy,
            String title,
            LocalDate date,
            String description,
            Long coverAssetId,
            int sortOrder) {}

    record UpdatedAnniversary(
            String title, LocalDate date, String description, Long coverAssetId, int sortOrder) {}
}

package com.langxi.babydiary.v3.anniversary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record Anniversary(long internalId, UUID id, UUID spaceId, String title, LocalDate date,
                          String description, UUID coverAssetId, int sortOrder,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
}

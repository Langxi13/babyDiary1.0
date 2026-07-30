package com.langxi.babydiary.space.domain;

import java.util.UUID;

public record SpaceSummary(UUID id, String name, String type, String role,
                           String defaultVisibility, long storageQuotaBytes, long storageUsedBytes) {
}

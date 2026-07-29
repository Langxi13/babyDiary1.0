package com.langxi.babydiary.v3.space.domain;

import java.util.UUID;

public record SpaceSummary(UUID id, String name, String type, String role,
                           String defaultVisibility, long storageQuotaBytes, long storageUsedBytes) {
}

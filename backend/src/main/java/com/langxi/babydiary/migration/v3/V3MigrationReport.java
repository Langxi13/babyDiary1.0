package com.langxi.babydiary.migration.v3;

import java.util.List;
import java.util.Map;

public record V3MigrationReport(
        String phase,
        boolean valid,
        Map<String, Long> counts,
        List<String> checks,
        List<String> failures
) {
}

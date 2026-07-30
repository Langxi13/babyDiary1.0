package com.langxi.babydiary.platform.application;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataRetentionWorker {
    private static final Logger log = LoggerFactory.getLogger(DataRetentionWorker.class);
    private static final int BATCH_SIZE = 1000;

    private final RetentionRepository retention;
    private final boolean enabled;
    private final int syncDays;

    public DataRetentionWorker(
            RetentionRepository retention,
            @Value("${app.retention.enabled:true}") boolean enabled,
            @Value("${app.retention.sync-days:90}") int syncDays) {
        this.retention = retention;
        this.enabled = enabled;
        this.syncDays = Math.max(30, syncDays);
    }

    @Scheduled(cron = "${app.retention.data-cleanup-cron:0 45 4 * * *}", zone = "UTC")
    @Transactional
    public void clean() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime syncCutoff = now.minusDays(syncDays);
        retention.recordSyncBaselines(syncCutoff);
        int deleted = 0;
        deleted += retention.deleteSyncChanges(syncCutoff, BATCH_SIZE);
        deleted += retention.deleteExpiredSyncOperations(now, BATCH_SIZE);
        deleted += retention.deleteExpiredAuthSessions(now.minusDays(7), BATCH_SIZE);
        deleted += retention.deleteExpiredAccountTokens(now.minusDays(7), BATCH_SIZE);
        deleted += retention.deleteUsedRecoveryCodes(now.minusDays(30), BATCH_SIZE);
        deleted += retention.deleteCompletedJobs(now.minusDays(30), BATCH_SIZE);
        deleted += retention.deleteCompletedOutboxEvents(now.minusDays(14), BATCH_SIZE);
        if (deleted > 0) log.info("Data retention removed {} expired rows", deleted);
    }
}

package com.langxi.babydiary.platform.application;

import java.time.LocalDateTime;

public interface RetentionRepository {
    int recordSyncBaselines(LocalDateTime cutoff);

    int deleteSyncChanges(LocalDateTime cutoff, int limit);

    int deleteExpiredSyncOperations(LocalDateTime now, int limit);

    int deleteExpiredAuthSessions(LocalDateTime cutoff, int limit);

    int deleteExpiredAccountTokens(LocalDateTime cutoff, int limit);

    int deleteUsedRecoveryCodes(LocalDateTime cutoff, int limit);

    int deleteCompletedJobs(LocalDateTime cutoff, int limit);

    int deleteCompletedOutboxEvents(LocalDateTime cutoff, int limit);
}

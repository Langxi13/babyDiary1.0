package com.langxi.babydiary.platform.infrastructure;

import com.langxi.babydiary.platform.application.RetentionRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisRetentionRepository implements RetentionRepository {
    private final RetentionMapper mapper;

    public MyBatisRetentionRepository(RetentionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int recordSyncBaselines(LocalDateTime cutoff) {
        return mapper.recordSyncBaselines(cutoff);
    }

    @Override
    public int deleteSyncChanges(LocalDateTime cutoff, int limit) {
        return mapper.deleteSyncChanges(cutoff, limit);
    }

    @Override
    public int deleteExpiredSyncOperations(LocalDateTime now, int limit) {
        return mapper.deleteExpiredSyncOperations(now, limit);
    }

    @Override
    public int deleteExpiredAuthSessions(LocalDateTime cutoff, int limit) {
        return mapper.deleteExpiredAuthSessions(cutoff, limit);
    }

    @Override
    public int deleteExpiredAccountTokens(LocalDateTime cutoff, int limit) {
        return mapper.deleteExpiredAccountTokens(cutoff, limit);
    }

    @Override
    public int deleteUsedRecoveryCodes(LocalDateTime cutoff, int limit) {
        return mapper.deleteUsedRecoveryCodes(cutoff, limit);
    }

    @Override
    public int deleteCompletedJobs(LocalDateTime cutoff, int limit) {
        return mapper.deleteCompletedJobs(cutoff, limit);
    }

    @Override
    public int deleteCompletedOutboxEvents(LocalDateTime cutoff, int limit) {
        return mapper.deleteCompletedOutboxEvents(cutoff, limit);
    }
}

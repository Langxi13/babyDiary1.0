package com.langxi.babydiary.platform.infrastructure;

import com.langxi.babydiary.platform.application.BackgroundJobRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisBackgroundJobRepository implements BackgroundJobRepository {
    private final BackgroundJobMapper mapper;

    public MyBatisBackgroundJobRepository(BackgroundJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int claim(String claimToken, LocalDateTime now, List<String> types) {
        return mapper.claim(claimToken, now, types);
    }

    @Override
    public int enqueue(NewJob job) {
        return mapper.enqueue(
                new BackgroundJobMapper.NewJob(
                        job.publicId(),
                        job.spaceId(),
                        job.createdBy(),
                        job.jobType(),
                        job.dedupeKey(),
                        job.payload(),
                        job.maxAttempts(),
                        job.availableAt()));
    }

    @Override
    public Job findClaimed(String claimToken) {
        BackgroundJobMapper.JobRow row = mapper.findClaimed(claimToken);
        return row == null
                ? null
                : new Job(
                        row.jobId(),
                        row.jobType(),
                        row.payload(),
                        row.attemptCount(),
                        row.maxAttempts());
    }

    @Override
    public int succeed(long jobId, String claimToken, String result, LocalDateTime now) {
        return mapper.succeed(jobId, claimToken, result, now);
    }

    @Override
    public int fail(
            long jobId,
            String claimToken,
            boolean terminal,
            LocalDateTime availableAt,
            String error,
            LocalDateTime now) {
        return mapper.fail(jobId, claimToken, terminal, availableAt, error, now);
    }

    @Override
    public int recoverRetryable(LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.recoverRetryable(staleBefore, now);
    }

    @Override
    public int failExhausted(LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.failExhausted(staleBefore, now);
    }
}

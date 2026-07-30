package com.langxi.babydiary.platform.application;

import java.time.LocalDateTime;
import java.util.List;

public interface BackgroundJobRepository {
    int claim(String claimToken, LocalDateTime now, List<String> types);

    int enqueue(NewJob job);

    Job findClaimed(String claimToken);

    int succeed(long jobId, String claimToken, String result, LocalDateTime now);

    int fail(
            long jobId,
            String claimToken,
            boolean terminal,
            LocalDateTime availableAt,
            String error,
            LocalDateTime now);

    int recoverRetryable(LocalDateTime staleBefore, LocalDateTime now);

    int failExhausted(LocalDateTime staleBefore, LocalDateTime now);

    record Job(long jobId, String jobType, String payload, int attemptCount, int maxAttempts) {}

    record NewJob(
            byte[] publicId,
            Long spaceId,
            Long createdBy,
            String jobType,
            String dedupeKey,
            String payload,
            int maxAttempts,
            LocalDateTime availableAt) {}
}

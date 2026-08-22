package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BackgroundJobQueue {
    private final BackgroundJobRepository jobs;
    private final ObjectMapper json;
    private final WorkerPollSignal pollSignal;

    public BackgroundJobQueue(
            BackgroundJobRepository jobs, ObjectMapper json, WorkerPollSignal pollSignal) {
        this.jobs = jobs;
        this.json = json;
        this.pollSignal = pollSignal;
    }

    public boolean enqueue(
            Long spaceId,
            Long createdBy,
            String type,
            String dedupeKey,
            Map<String, ?> payload,
            int maxAttempts) {
        try {
            boolean inserted =
                    jobs.enqueue(
                                    new BackgroundJobRepository.NewJob(
                                            BinaryUuid.toBytes(UUID.randomUUID()),
                                            spaceId,
                                            createdBy,
                                            type,
                                            dedupeKey,
                                            json.writeValueAsString(payload),
                                            Math.max(1, Math.min(maxAttempts, 10)),
                                            LocalDateTime.now(ZoneOffset.UTC)))
                            == 1;
            if (inserted) pollSignal.jobEnqueued();
            return inserted;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Background job payload is not serializable", exception);
        }
    }
}

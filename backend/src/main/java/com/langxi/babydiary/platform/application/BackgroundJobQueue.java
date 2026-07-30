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

    public BackgroundJobQueue(BackgroundJobRepository jobs, ObjectMapper json) {
        this.jobs = jobs;
        this.json = json;
    }

    public boolean enqueue(
            Long spaceId,
            Long createdBy,
            String type,
            String dedupeKey,
            Map<String, ?> payload,
            int maxAttempts) {
        try {
            return jobs.enqueue(
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Background job payload is not serializable", exception);
        }
    }
}

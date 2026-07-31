package com.langxi.babydiary.media.application;

import com.langxi.babydiary.platform.application.BackgroundJobQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaDerivativeCoordinator {
    static final int TARGET_VERSION = 2;
    private final MediaRepository media;
    private final BackgroundJobQueue jobs;
    private final boolean enabled;
    private final int batchSize;

    public MediaDerivativeCoordinator(
            MediaRepository media,
            BackgroundJobQueue jobs,
            @Value("${app.media.derivative-backfill-enabled:true}") boolean enabled,
            @Value("${app.media.derivative-backfill-batch-size:12}") int batchSize) {
        this.media = media;
        this.jobs = jobs;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 24));
    }

    @Scheduled(
            initialDelayString = "${app.media.derivative-backfill-initial-delay-ms:60000}",
            fixedDelayString = "${app.media.derivative-backfill-delay-ms:300000}")
    public void enqueueMissing() {
        if (!enabled) return;
        for (MediaRepository.DerivativeCandidate candidate :
                media.findDerivativeCandidates(TARGET_VERSION, batchSize)) {
            jobs.enqueue(
                    candidate.spaceId(),
                    candidate.ownerId(),
                    "MEDIA_PROCESS",
                    "asset:v2:" + candidate.assetId(),
                    java.util.Map.of(
                            "spaceId", candidate.spacePublicId().toString(),
                            "assetId", candidate.assetId().toString()),
                    5);
        }
    }
}

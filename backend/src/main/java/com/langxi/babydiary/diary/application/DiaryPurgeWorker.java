package com.langxi.babydiary.diary.application;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DiaryPurgeWorker {
    private static final Logger log = LoggerFactory.getLogger(DiaryPurgeWorker.class);

    private final DiaryRepository diaries;
    private final DiaryPurgeService purge;
    private final boolean enabled;
    private final int retentionDays;

    public DiaryPurgeWorker(
            DiaryRepository diaries,
            DiaryPurgeService purge,
            @Value("${app.retention.enabled:true}") boolean enabled,
            @Value("${app.retention.diary-trash-days:30}") int retentionDays) {
        this.diaries = diaries;
        this.purge = purge;
        this.enabled = enabled;
        this.retentionDays = Math.max(7, retentionDays);
    }

    @Scheduled(cron = "${app.retention.diary-purge-cron:0 25 4 * * *}", zone = "UTC")
    public void purgeExpired() {
        if (!enabled) return;
        int count = 0;
        for (DiaryRepository.PurgeCandidate candidate :
                diaries.findPurgeCandidates(
                        LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays), 50)) {
            try {
                if (purge.purge(candidate)) count++;
            } catch (RuntimeException exception) {
                log.warn("Unable to purge diary {}", candidate.id(), exception);
            }
        }
        if (count > 0) log.info("Purged {} expired diary entries", count);
    }
}

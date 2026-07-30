package com.langxi.babydiary.diary.application;

import com.langxi.babydiary.platform.application.ChangeRecorder;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryPurgeService {
    private final DiaryRepository diaries;
    private final ChangeRecorder changes;
    private final ReadCacheInvalidator cacheInvalidator;

    public DiaryPurgeService(
            DiaryRepository diaries,
            ChangeRecorder changes,
            ReadCacheInvalidator cacheInvalidator) {
        this.diaries = diaries;
        this.changes = changes;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public boolean purge(DiaryRepository.PurgeCandidate candidate) {
        if (!diaries.permanentlyDelete(candidate.internalId(), candidate.version())) return false;
        changes.record(
                candidate.spaceInternalId(),
                candidate.authorId(),
                "DIARY",
                candidate.id(),
                "DIARY_PURGED",
                candidate.version() + 1,
                Map.of("retention", true));
        cacheInvalidator.diary(candidate.spaceId());
        return true;
    }
}

package com.langxi.babydiary.ai.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReportPersistenceService {
    private final AiReportRepository reports;

    public AiReportPersistenceService(AiReportRepository reports) {
        this.reports = reports;
    }

    @Transactional
    public AiReportRepository.Report save(
            AiReportRepository.NewReport report, List<AiReportRepository.DiaryInput> diaries) {
        long reportId = reports.insert(report);
        for (AiReportRepository.DiaryInput diary : diaries) {
            reports.insertDiary(report.spaceId(), reportId, diary.internalId());
        }
        return reports.findByPublicId(report.spaceId(), report.createdBy(), report.publicId())
                .orElseThrow(() -> new IllegalStateException("AI report was not persisted"));
    }
}

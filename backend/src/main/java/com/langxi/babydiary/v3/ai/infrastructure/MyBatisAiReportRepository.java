package com.langxi.babydiary.v3.ai.infrastructure;

import com.langxi.babydiary.v3.ai.application.AiReportRepository;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisAiReportRepository implements AiReportRepository {
    private final AiReportMapper mapper;

    public MyBatisAiReportRepository(AiReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Report> findForCreator(long spaceId, long creatorId) {
        return mapper.findForCreator(spaceId, creatorId).stream().map(this::report).toList();
    }

    @Override
    public Optional<Report> findByPublicId(long spaceId, long creatorId, UUID publicId) {
        return Optional.ofNullable(mapper.findByPublicId(spaceId, creatorId, BinaryUuid.toBytes(publicId))).map(this::report);
    }

    @Override
    public boolean delete(long spaceId, long creatorId, UUID publicId) {
        return mapper.delete(spaceId, creatorId, BinaryUuid.toBytes(publicId)) == 1;
    }

    @Override
    public long insert(NewReport value) {
        AiReportMapper.ReportInsert row = new AiReportMapper.ReportInsert(BinaryUuid.toBytes(value.publicId()), value.spaceId(),
                value.createdBy(), value.periodType(), value.start(), value.end(), value.title(), value.markdown(),
                value.diaryCount(), value.model());
        mapper.insert(row);
        if (row.getReportId() == null) throw new IllegalStateException("AI report insert returned no ID");
        return row.getReportId();
    }

    @Override
    public void insertDiary(long spaceId, long reportId, long diaryId) {
        mapper.insertDiary(spaceId, reportId, diaryId);
    }

    @Override
    public List<DiaryInput> findDiaries(long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findDiaries(spaceId, accountId, start, end).stream().map(row -> new DiaryInput(row.diaryId(),
                BinaryUuid.fromBytes(row.publicId()), row.diaryDate(), row.title(), row.contentText(), row.moodKey())).toList();
    }

    private Report report(AiReportMapper.ReportRow row) {
        return new Report(row.reportId(), BinaryUuid.fromBytes(row.publicId()), BinaryUuid.fromBytes(row.spacePublicId()),
                row.periodType(), row.periodStart(), row.periodEnd(), row.title(), row.contentMarkdown(), row.diaryCount(),
                row.model(), row.createdAt());
    }
}

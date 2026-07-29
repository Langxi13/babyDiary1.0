package com.langxi.babydiary.v3.ai.infrastructure;

import com.langxi.babydiary.v3.ai.application.AiReportRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiReportMapper {
    @Select("""
            SELECT r.report_id,r.public_id,s.public_id AS space_public_id,r.period_type,r.period_start,r.period_end,
                   r.title,r.content_markdown,r.diary_count,r.model,r.created_at
            FROM ai_report r JOIN diary_space s ON s.space_id=r.space_id
            WHERE r.space_id=#{spaceId} AND r.created_by=#{creatorId}
            ORDER BY r.created_at DESC,r.report_id DESC
            """)
    List<ReportRow> findForCreator(@Param("spaceId") long spaceId, @Param("creatorId") long creatorId);

    @Select("""
            SELECT r.report_id,r.public_id,s.public_id AS space_public_id,r.period_type,r.period_start,r.period_end,
                   r.title,r.content_markdown,r.diary_count,r.model,r.created_at
            FROM ai_report r JOIN diary_space s ON s.space_id=r.space_id
            WHERE r.space_id=#{spaceId} AND r.created_by=#{creatorId} AND r.public_id=#{publicId}
            """)
    ReportRow findByPublicId(@Param("spaceId") long spaceId, @Param("creatorId") long creatorId,
                             @Param("publicId") byte[] publicId);

    @Delete("DELETE FROM ai_report WHERE space_id=#{spaceId} AND created_by=#{creatorId} AND public_id=#{publicId}")
    int delete(@Param("spaceId") long spaceId, @Param("creatorId") long creatorId,
               @Param("publicId") byte[] publicId);

    @Insert("""
            INSERT INTO ai_report(public_id,space_id,created_by,period_type,period_start,period_end,title,
              content_markdown,diary_count,model,created_at)
            VALUES(#{publicId},#{spaceId},#{createdBy},#{periodType},#{start},#{end},#{title},#{markdown},#{diaryCount},#{model},UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "reportId")
    void insert(ReportInsert row);

    @Insert("INSERT INTO ai_report_diary(space_id,report_id,diary_id) VALUES(#{spaceId},#{reportId},#{diaryId})")
    void insertDiary(@Param("spaceId") long spaceId, @Param("reportId") long reportId, @Param("diaryId") long diaryId);

    @Select("""
            SELECT d.diary_id,d.public_id,d.diary_date,d.title,d.content_text,d.mood_key
            FROM diary d
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND d.diary_date BETWEEN #{start} AND #{end}
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
            ORDER BY d.diary_date,d.diary_id
            """)
    List<DiaryRow> findDiaries(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                               @Param("start") LocalDate start, @Param("end") LocalDate end);

    final class ReportInsert {
        private Long reportId;
        private final byte[] publicId;
        private final long spaceId;
        private final long createdBy;
        private final String periodType;
        private final LocalDate start;
        private final LocalDate end;
        private final String title;
        private final String markdown;
        private final int diaryCount;
        private final String model;

        public ReportInsert(byte[] publicId, long spaceId, long createdBy, String periodType, LocalDate start,
                            LocalDate end, String title, String markdown, int diaryCount, String model) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.createdBy = createdBy;
            this.periodType = periodType;
            this.start = start;
            this.end = end;
            this.title = title;
            this.markdown = markdown;
            this.diaryCount = diaryCount;
            this.model = model;
        }

        public Long getReportId() { return reportId; }
        public void setReportId(Long reportId) { this.reportId = reportId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public long getCreatedBy() { return createdBy; }
        public String getPeriodType() { return periodType; }
        public LocalDate getStart() { return start; }
        public LocalDate getEnd() { return end; }
        public String getTitle() { return title; }
        public String getMarkdown() { return markdown; }
        public int getDiaryCount() { return diaryCount; }
        public String getModel() { return model; }
    }

    final class ReportRow {
        private long reportId;
        private byte[] publicId;
        private byte[] spacePublicId;
        private String periodType;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String title;
        private String contentMarkdown;
        private int diaryCount;
        private String model;
        private LocalDateTime createdAt;

        public ReportRow() {
        }

        public long reportId() { return reportId; }
        public byte[] publicId() { return publicId; }
        public byte[] spacePublicId() { return spacePublicId; }
        public String periodType() { return periodType; }
        public LocalDate periodStart() { return periodStart; }
        public LocalDate periodEnd() { return periodEnd; }
        public String title() { return title; }
        public String contentMarkdown() { return contentMarkdown; }
        public int diaryCount() { return diaryCount; }
        public String model() { return model; }
        public LocalDateTime createdAt() { return createdAt; }

        public void setReportId(long reportId) { this.reportId = reportId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setSpacePublicId(byte[] spacePublicId) { this.spacePublicId = spacePublicId; }
        public void setPeriodType(String periodType) { this.periodType = periodType; }
        public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
        public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
        public void setTitle(String title) { this.title = title; }
        public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }
        public void setDiaryCount(int diaryCount) { this.diaryCount = diaryCount; }
        public void setModel(String model) { this.model = model; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    final class DiaryRow {
        private long diaryId;
        private byte[] publicId;
        private LocalDate diaryDate;
        private String title;
        private String contentText;
        private String moodKey;

        public DiaryRow() {
        }

        public long diaryId() { return diaryId; }
        public byte[] publicId() { return publicId; }
        public LocalDate diaryDate() { return diaryDate; }
        public String title() { return title; }
        public String contentText() { return contentText; }
        public String moodKey() { return moodKey; }

        public void setDiaryId(long diaryId) { this.diaryId = diaryId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setDiaryDate(LocalDate diaryDate) { this.diaryDate = diaryDate; }
        public void setTitle(String title) { this.title = title; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public void setMoodKey(String moodKey) { this.moodKey = moodKey; }
    }
}

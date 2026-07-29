package com.langxi.babydiary.v3.diary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DiaryReadMapper {
    @Select("""
            SELECT d.public_id,d.diary_date,d.title,d.mood_key,
                   (SELECT COUNT(*) FROM diary_media dm WHERE dm.diary_id=d.diary_id) AS media_count
            FROM diary d
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
              AND d.diary_date BETWEEN #{start} AND #{end}
            ORDER BY d.diary_date,d.diary_id
            """)
    List<CalendarRow> findCalendar(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                                   @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Select("""
            SELECT YEAR(d.diary_date) AS diary_year,MONTH(d.diary_date) AS diary_month,COUNT(*) AS diary_count
            FROM diary d
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
            GROUP BY YEAR(d.diary_date),MONTH(d.diary_date)
            ORDER BY diary_year DESC,diary_month DESC
            """)
    List<MonthCountRow> findMonthCounts(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    @Select("""
            SELECT DATE_SUB(d.diary_date,INTERVAL WEEKDAY(d.diary_date) DAY) AS week_start,COUNT(*) AS diary_count
            FROM diary d
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
              AND d.diary_date BETWEEN #{start} AND #{end}
            GROUP BY DATE_SUB(d.diary_date,INTERVAL WEEKDAY(d.diary_date) DAY)
            ORDER BY week_start DESC
            """)
    List<WeekCountRow> findWeekCounts(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                                      @Param("start") LocalDate start, @Param("end") LocalDate end);

    final class CalendarRow {
        private byte[] publicId;
        private LocalDate diaryDate;
        private String title;
        private String moodKey;
        private int mediaCount;

        public CalendarRow() {
        }

        public byte[] publicId() { return publicId; }
        public LocalDate diaryDate() { return diaryDate; }
        public String title() { return title; }
        public String moodKey() { return moodKey; }
        public int mediaCount() { return mediaCount; }

        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setDiaryDate(LocalDate diaryDate) { this.diaryDate = diaryDate; }
        public void setTitle(String title) { this.title = title; }
        public void setMoodKey(String moodKey) { this.moodKey = moodKey; }
        public void setMediaCount(int mediaCount) { this.mediaCount = mediaCount; }
    }

    record MonthCountRow(int diaryYear, int diaryMonth, long diaryCount) {
    }

    record WeekCountRow(LocalDate weekStart, long diaryCount) {
    }
}

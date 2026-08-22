package com.langxi.babydiary.diary.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DiaryReadMapper {
    @Select(
            """
            SELECT d.public_id,d.diary_date,d.title,d.mood_key,d.locked,
                   (SELECT COUNT(*) FROM diary_media dm WHERE dm.diary_id=d.diary_id) AS media_count
            FROM diary d
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
              AND d.diary_date BETWEEN #{start} AND #{end}
            ORDER BY d.diary_date,d.diary_id
            """)
    List<CalendarRow> findCalendar(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Select(
            """
            <script>
            SELECT YEAR(d.diary_date) AS diary_year,MONTH(d.diary_date) AS diary_month,COUNT(*) AS diary_count
                  ,COALESCE(SUM(media.media_count),0) AS media_count
            FROM diary d LEFT JOIN (
              SELECT dm.diary_id,COUNT(*) AS media_count
              FROM diary_media dm JOIN media_asset a ON a.asset_id=dm.asset_id
              WHERE a.media_type='IMAGE' AND a.status='READY' AND a.deleted_at IS NULL
              GROUP BY dm.diary_id
            ) media ON media.diary_id=d.diary_id
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
            <if test="mood != null">
              <if test="!elevated">AND d.locked=0</if>
              AND d.mood_key=#{mood}
            </if>
            <if test="tagId != null">
              <if test="!elevated">AND d.locked=0</if>
              AND EXISTS (SELECT 1 FROM diary_tag dt JOIN tag t ON t.tag_id=dt.tag_id
                          WHERE dt.diary_id=d.diary_id AND t.public_id=#{tagId})
            </if>
            GROUP BY YEAR(d.diary_date),MONTH(d.diary_date)
            ORDER BY diary_year DESC,diary_month DESC
            </script>
            """)
    List<MonthCountRow> findMonthCounts(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("mood") String mood,
            @Param("tagId") byte[] tagId,
            @Param("elevated") boolean elevated);

    @Select(
            """
            <script>
            SELECT DATE_SUB(d.diary_date,INTERVAL WEEKDAY(d.diary_date) DAY) AS week_start,COUNT(*) AS diary_count
            FROM diary d
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
              AND d.diary_date BETWEEN #{start} AND #{end}
            <if test="mood != null">
              <if test="!elevated">AND d.locked=0</if>
              AND d.mood_key=#{mood}
            </if>
            <if test="tagId != null">
              <if test="!elevated">AND d.locked=0</if>
              AND EXISTS (SELECT 1 FROM diary_tag dt JOIN tag t ON t.tag_id=dt.tag_id
                          WHERE dt.diary_id=d.diary_id AND t.public_id=#{tagId})
            </if>
            GROUP BY DATE_SUB(d.diary_date,INTERVAL WEEKDAY(d.diary_date) DAY)
            ORDER BY week_start DESC
            </script>
            """)
    List<WeekCountRow> findWeekCounts(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("mood") String mood,
            @Param("tagId") byte[] tagId,
            @Param("elevated") boolean elevated);

    final class CalendarRow {
        private byte[] publicId;
        private LocalDate diaryDate;
        private String title;
        private String moodKey;
        private int mediaCount;
        private boolean locked;

        public CalendarRow() {}

        public byte[] publicId() {
            return publicId;
        }

        public LocalDate diaryDate() {
            return diaryDate;
        }

        public String title() {
            return title;
        }

        public String moodKey() {
            return moodKey;
        }

        public int mediaCount() {
            return mediaCount;
        }

        public boolean locked() {
            return locked;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setDiaryDate(LocalDate diaryDate) {
            this.diaryDate = diaryDate;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setMoodKey(String moodKey) {
            this.moodKey = moodKey;
        }

        public void setMediaCount(int mediaCount) {
            this.mediaCount = mediaCount;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }
    }

    record MonthCountRow(int diaryYear, int diaryMonth, long diaryCount, long mediaCount) {}

    record WeekCountRow(LocalDate weekStart, long diaryCount) {}
}

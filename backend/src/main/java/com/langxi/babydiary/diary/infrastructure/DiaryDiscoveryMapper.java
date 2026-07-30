package com.langxi.babydiary.diary.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DiaryDiscoveryMapper {
    String VISIBLE =
            " d.space_id=#{spaceId} AND d.deleted_at IS NULL AND d.locked=0 "
                    + "AND (d.visibility='SHARED' OR d.author_id=#{accountId})";

    @Select(
            """
            SELECT d.public_id,d.title,LEFT(d.content_text,500) AS snippet,d.diary_date,
                   MATCH(d.title,d.content_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM diary d WHERE """
                    + VISIBLE
                    + """
              AND MATCH(d.title,d.content_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY score DESC,d.diary_date DESC,d.diary_id DESC LIMIT #{limit}
            """)
    List<SearchRow> searchFullText(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("query") String query,
            @Param("limit") int limit);

    @Select(
            """
            SELECT d.public_id,d.title,LEFT(d.content_text,500) AS snippet,d.diary_date,1.0 AS score
            FROM diary d WHERE """
                    + VISIBLE
                    + """
              AND (d.title LIKE CONCAT('%',#{query},'%') OR d.content_text LIKE CONCAT('%',#{query},'%'))
            ORDER BY d.diary_date DESC,d.diary_id DESC LIMIT #{limit}
            """)
    List<SearchRow> searchLike(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("query") String query,
            @Param("limit") int limit);

    @Select(
            "SELECT d.diary_date AS day,COUNT(*) AS item_count FROM diary d WHERE "
                    + VISIBLE
                    + " AND d.diary_date BETWEEN #{start} AND #{end} GROUP BY d.diary_date ORDER BY d.diary_date")
    List<DayRow> findDays(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Select(
            "SELECT d.mood_key,COUNT(*) AS item_count FROM diary d WHERE "
                    + VISIBLE
                    + " AND d.diary_date BETWEEN #{start} AND #{end} AND d.mood_key IS NOT NULL "
                    + "GROUP BY d.mood_key ORDER BY item_count DESC,d.mood_key")
    List<MoodRow> findMoods(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Select(
            "SELECT DATE_FORMAT(d.diary_date,'%Y-%m') AS month,COUNT(*) AS item_count FROM diary d WHERE "
                    + VISIBLE
                    + " AND d.diary_date BETWEEN #{start} AND #{end} GROUP BY DATE_FORMAT(d.diary_date,'%Y-%m') ORDER BY month")
    List<MonthRow> findMonths(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Select(
            "SELECT COUNT(DISTINCT dm.asset_id) FROM diary d JOIN diary_media dm ON dm.diary_id=d.diary_id "
                    + "JOIN media_asset a ON a.asset_id=dm.asset_id AND a.media_type='IMAGE' AND a.deleted_at IS NULL WHERE "
                    + VISIBLE
                    + " AND d.diary_date BETWEEN #{start} AND #{end}")
    int countPhotos(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    final class SearchRow {
        private byte[] publicId;
        private String title;
        private String snippet;
        private LocalDate diaryDate;
        private double score;

        public SearchRow() {}

        public byte[] getPublicId() {
            return publicId;
        }

        public String getTitle() {
            return title;
        }

        public String getSnippet() {
            return snippet;
        }

        public LocalDate getDiaryDate() {
            return diaryDate;
        }

        public double getScore() {
            return score;
        }

        public void setPublicId(byte[] v) {
            publicId = v;
        }

        public void setTitle(String v) {
            title = v;
        }

        public void setSnippet(String v) {
            snippet = v;
        }

        public void setDiaryDate(LocalDate v) {
            diaryDate = v;
        }

        public void setScore(double v) {
            score = v;
        }
    }

    record DayRow(LocalDate day, long itemCount) {}

    record MoodRow(String moodKey, long itemCount) {}

    record MonthRow(String month, long itemCount) {}
}

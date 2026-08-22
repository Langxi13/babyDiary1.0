package com.langxi.babydiary.transfer.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransferMapper {
    @Select("SELECT name FROM diary_space WHERE space_id=#{spaceId} AND deleted_at IS NULL")
    String findSpaceName(long spaceId);

    @Select(
            """
            <script>
            SELECT diary_id,public_id,title,diary_date,content_html,mood_key,visibility,locked
            FROM diary
            WHERE space_id=#{spaceId} AND deleted_at IS NULL
              AND (visibility='SHARED' OR author_id=#{accountId})
            <if test="startDate != null">AND diary_date &gt;= #{startDate}</if>
            <if test="endDate != null">AND diary_date &lt;= #{endDate}</if>
            ORDER BY diary_date,diary_id
            LIMIT #{limit}
            </script>
            """)
    List<DiaryRow> findDiaries(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit);

    @Select(
            """
            SELECT COUNT(DISTINCT d.diary_id) AS diary_count,
                   COUNT(a.asset_id) AS media_count,
                   COALESCE(SUM(COALESCE((
                     SELECT v.size_bytes FROM media_variant v
                     WHERE v.asset_id=a.asset_id AND v.variant_type='ORIGINAL'
                       AND v.status='READY' AND v.deleted_at IS NULL
                     ORDER BY CASE v.profile WHEN 'source' THEN 0 WHEN 'default' THEN 1 ELSE 2 END,
                              v.variant_id LIMIT 1
                   ),0)),0) AS total_media_bytes,
                   COALESCE(MAX(COALESCE((
                     SELECT v.size_bytes FROM media_variant v
                     WHERE v.asset_id=a.asset_id AND v.variant_type='ORIGINAL'
                       AND v.status='READY' AND v.deleted_at IS NULL
                     ORDER BY CASE v.profile WHEN 'source' THEN 0 WHEN 'default' THEN 1 ELSE 2 END,
                              v.variant_id LIMIT 1
                   ),0)),0) AS max_media_bytes,
                   COALESCE(MAX(d.locked OR (a.asset_id IS NOT NULL AND EXISTS (
                     SELECT 1 FROM diary_media lock_dm JOIN diary lock_d ON lock_d.diary_id=lock_dm.diary_id
                     WHERE lock_dm.asset_id=a.asset_id AND lock_d.locked=1
                   ))),0) AS requires_step_up
            FROM diary d
            LEFT JOIN diary_media dm ON dm.diary_id=d.diary_id
            LEFT JOIN media_asset a ON a.asset_id=dm.asset_id AND a.deleted_at IS NULL AND a.status='READY'
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
            """)
    ExportPreflightRow exportPreflight(
            @Param("spaceId") long spaceId, @Param("accountId") long accountId);

    @Select(
            """
            <script>
            SELECT diary_id,public_id,title,diary_date,content_html,mood_key,visibility,locked
            FROM diary
            WHERE space_id=#{spaceId} AND deleted_at IS NULL
              AND (visibility='SHARED' OR author_id=#{accountId})
            <if test="afterDate != null">
              AND (diary_date&gt;#{afterDate} OR (diary_date=#{afterDate} AND diary_id&gt;#{afterId}))
            </if>
            ORDER BY diary_date,diary_id LIMIT #{limit}
            </script>
            """)
    List<DiaryRow> findDiaryBatch(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("afterDate") LocalDate afterDate,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);

    @Select(
            """
            <script>
            SELECT dt.diary_id,t.name,t.color FROM diary_tag dt JOIN tag t ON t.tag_id=dt.tag_id
            WHERE dt.diary_id IN
            <foreach collection="diaryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY dt.diary_id,t.name,t.tag_id
            </script>
            """)
    List<TagRow> findTags(@Param("diaryIds") List<Long> diaryIds);

    @Select(
            """
            <script>
            SELECT dm.diary_id,a.public_id,a.original_filename,a.media_type,a.caption,a.taken_at,dm.position
            FROM diary_media dm JOIN media_asset a ON a.asset_id=dm.asset_id
            WHERE a.deleted_at IS NULL AND a.status='READY' AND dm.diary_id IN
            <foreach collection="diaryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY dm.diary_id,dm.position,a.asset_id
            </script>
            """)
    List<MediaRow> findMedia(@Param("diaryIds") List<Long> diaryIds);

    @Select(
            """
            <script>
            SELECT c.diary_id,a.username,c.content,c.created_at
            FROM diary_comment c JOIN account a ON a.account_id=c.author_id
            WHERE c.deleted_at IS NULL AND c.diary_id IN
            <foreach collection="diaryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY c.diary_id,c.created_at,c.comment_id
            </script>
            """)
    List<CommentRow> findComments(@Param("diaryIds") List<Long> diaryIds);

    @Select("SELECT COUNT(*) FROM diary WHERE space_id=#{spaceId} AND public_id=#{publicId}")
    int countDiary(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    record DiaryRow(
            long diaryId,
            byte[] publicId,
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String moodKey,
            String visibility,
            boolean locked) {}

    record TagRow(long diaryId, String name, String color) {}

    record MediaRow(
            long diaryId,
            byte[] publicId,
            String originalFilename,
            String mediaType,
            String caption,
            LocalDateTime takenAt,
            int position) {}

    record CommentRow(long diaryId, String username, String content, LocalDateTime createdAt) {}

    record ExportPreflightRow(
            long diaryCount,
            long mediaCount,
            long totalMediaBytes,
            long maxMediaBytes,
            boolean requiresStepUp) {}
}

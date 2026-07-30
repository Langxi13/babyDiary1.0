package com.langxi.babydiary.transfer.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TransferMapper {
    @Select("SELECT name FROM diary_space WHERE space_id=#{spaceId} AND deleted_at IS NULL")
    String findSpaceName(long spaceId);

    @Select("""
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
    List<DiaryRow> findDiaries(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                                @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT dt.diary_id,t.name,t.color FROM diary_tag dt JOIN tag t ON t.tag_id=dt.tag_id
            WHERE dt.diary_id IN
            <foreach collection="diaryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY dt.diary_id,t.name,t.tag_id
            </script>
            """)
    List<TagRow> findTags(@Param("diaryIds") List<Long> diaryIds);

    @Select("""
            <script>
            SELECT dm.diary_id,a.public_id,a.original_filename,a.media_type,a.caption,a.taken_at,dm.position
            FROM diary_media dm JOIN media_asset a ON a.asset_id=dm.asset_id
            WHERE a.deleted_at IS NULL AND a.status='READY' AND dm.diary_id IN
            <foreach collection="diaryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY dm.diary_id,dm.position,a.asset_id
            </script>
            """)
    List<MediaRow> findMedia(@Param("diaryIds") List<Long> diaryIds);

    @Select("""
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

    record DiaryRow(long diaryId, byte[] publicId, String title, LocalDate diaryDate, String contentHtml,
                    String moodKey, String visibility, boolean locked) {}
    record TagRow(long diaryId, String name, String color) {}
    record MediaRow(long diaryId, byte[] publicId, String originalFilename, String mediaType, String caption,
                    LocalDateTime takenAt, int position) {}
    record CommentRow(long diaryId, String username, String content, LocalDateTime createdAt) {}
}

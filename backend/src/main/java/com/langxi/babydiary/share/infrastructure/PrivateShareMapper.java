package com.langxi.babydiary.share.infrastructure;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PrivateShareMapper {
    @Select("""
            SELECT d.diary_id,d.author_id,d.locked,d.title,d.diary_date,d.content_html,d.mood_key,s.public_id AS space_public_id
            FROM diary d JOIN diary_space s ON s.space_id=d.space_id
            WHERE d.space_id=#{spaceId} AND d.public_id=#{diaryPublicId} AND d.deleted_at IS NULL
              AND (d.author_id=#{accountId} OR #{spaceOwner}=1)
            """)
    DiaryRow findManageableDiary(@Param("spaceId") long spaceId,@Param("diaryPublicId") byte[] diaryPublicId,
                                 @Param("accountId") long accountId,@Param("spaceOwner") boolean spaceOwner);

    @Insert("""
            INSERT INTO private_share(public_id,token_hash,space_id,diary_id,created_by,password_hash,expires_at,max_views)
            VALUES(#{publicId},#{tokenHash},#{spaceId},#{diaryId},#{createdBy},#{passwordHash},#{expiresAt},#{maxViews})
            """)
    @Options(useGeneratedKeys=true,keyProperty="shareId")
    void insert(ShareInsert row);

    @Select("""
            SELECT share_id,public_id,password_hash,expires_at,max_views,view_count,created_at
            FROM private_share WHERE diary_id=#{diaryId} AND created_by=#{accountId}
              AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(6)
              AND (max_views IS NULL OR view_count<max_views)
            ORDER BY created_at DESC,share_id DESC
            """)
    List<ShareRow> findActive(@Param("diaryId") long diaryId,@Param("accountId") long accountId);

    @Select("""
            SELECT ps.share_id,ps.public_id,ps.password_hash,ps.expires_at,ps.max_views,ps.view_count,ps.created_at,ps.space_id,
                   d.diary_id,d.author_id,d.locked,d.title,d.diary_date,d.content_html,d.mood_key,s.public_id AS space_public_id
            FROM private_share ps JOIN diary d ON d.diary_id=ps.diary_id
            JOIN diary_space s ON s.space_id=ps.space_id
            WHERE ps.token_hash=#{tokenHash} AND ps.revoked_at IS NULL AND d.deleted_at IS NULL FOR UPDATE
            """)
    OpenRow findForOpen(byte[] tokenHash);

    @Update("UPDATE private_share SET view_count=view_count+1 WHERE share_id=#{shareId} AND revoked_at IS NULL " +
            "AND expires_at>#{now} AND (max_views IS NULL OR view_count<max_views)")
    int incrementView(@Param("shareId") long shareId,@Param("now") LocalDateTime now);

    @Update("UPDATE private_share SET revoked_at=UTC_TIMESTAMP(6) WHERE public_id=#{publicId} " +
            "AND created_by=#{accountId} AND revoked_at IS NULL")
    int revoke(@Param("publicId") byte[] publicId,@Param("accountId") long accountId);

    @Select("""
            SELECT a.public_id,a.media_type,a.caption,a.taken_at,dm.position
            FROM diary_media dm JOIN media_asset a ON a.asset_id=dm.asset_id
            WHERE dm.diary_id=#{diaryId} AND a.deleted_at IS NULL AND a.status='READY'
            ORDER BY dm.position,a.asset_id
            """)
    List<MediaRow> findMedia(long diaryId);

    final class ShareInsert {
        private Long shareId;private final byte[] publicId;private final byte[] tokenHash;private final long spaceId;
        private final long diaryId;private final long createdBy;private final String passwordHash;
        private final LocalDateTime expiresAt;private final Integer maxViews;
        public ShareInsert(byte[] publicId,byte[] tokenHash,long spaceId,long diaryId,long createdBy,String passwordHash,LocalDateTime expiresAt,Integer maxViews){
            this.publicId=publicId;this.tokenHash=tokenHash;this.spaceId=spaceId;this.diaryId=diaryId;this.createdBy=createdBy;this.passwordHash=passwordHash;this.expiresAt=expiresAt;this.maxViews=maxViews;}
        public Long getShareId(){return shareId;}public void setShareId(Long v){shareId=v;}public byte[] getPublicId(){return publicId;}public byte[] getTokenHash(){return tokenHash;}
        public long getSpaceId(){return spaceId;}public long getDiaryId(){return diaryId;}public long getCreatedBy(){return createdBy;}public String getPasswordHash(){return passwordHash;}
        public LocalDateTime getExpiresAt(){return expiresAt;}public Integer getMaxViews(){return maxViews;}
    }
    class DiaryRow {
        private long diaryId;private long authorId;private boolean locked;private String title;private LocalDate diaryDate;
        private String contentHtml;private String moodKey;private byte[] spacePublicId;
        public long getDiaryId(){return diaryId;}public long getAuthorId(){return authorId;}public boolean isLocked(){return locked;}public String getTitle(){return title;}
        public LocalDate getDiaryDate(){return diaryDate;}public String getContentHtml(){return contentHtml;}public String getMoodKey(){return moodKey;}public byte[] getSpacePublicId(){return spacePublicId;}
        public void setDiaryId(long v){diaryId=v;}public void setAuthorId(long v){authorId=v;}public void setLocked(boolean v){locked=v;}public void setTitle(String v){title=v;}
        public void setDiaryDate(LocalDate v){diaryDate=v;}public void setContentHtml(String v){contentHtml=v;}public void setMoodKey(String v){moodKey=v;}public void setSpacePublicId(byte[] v){spacePublicId=v;}
    }
    class ShareRow {
        private long shareId;private byte[] publicId;private String passwordHash;private LocalDateTime expiresAt;private Integer maxViews;private int viewCount;private LocalDateTime createdAt;
        public long getShareId(){return shareId;}public byte[] getPublicId(){return publicId;}public String getPasswordHash(){return passwordHash;}public LocalDateTime getExpiresAt(){return expiresAt;}
        public Integer getMaxViews(){return maxViews;}public int getViewCount(){return viewCount;}public LocalDateTime getCreatedAt(){return createdAt;}
        public void setShareId(long v){shareId=v;}public void setPublicId(byte[] v){publicId=v;}public void setPasswordHash(String v){passwordHash=v;}public void setExpiresAt(LocalDateTime v){expiresAt=v;}
        public void setMaxViews(Integer v){maxViews=v;}public void setViewCount(int v){viewCount=v;}public void setCreatedAt(LocalDateTime v){createdAt=v;}
    }
    class OpenRow extends ShareRow {
        private long spaceId;private long diaryId;private long authorId;private boolean locked;private String title;private LocalDate diaryDate;private String contentHtml;private String moodKey;private byte[] spacePublicId;
        public long getSpaceId(){return spaceId;}public void setSpaceId(long v){spaceId=v;}
        public long getDiaryId(){return diaryId;}public long getAuthorId(){return authorId;}public boolean isLocked(){return locked;}public String getTitle(){return title;}public LocalDate getDiaryDate(){return diaryDate;}
        public String getContentHtml(){return contentHtml;}public String getMoodKey(){return moodKey;}public byte[] getSpacePublicId(){return spacePublicId;}
        public void setDiaryId(long v){diaryId=v;}public void setAuthorId(long v){authorId=v;}public void setLocked(boolean v){locked=v;}public void setTitle(String v){title=v;}public void setDiaryDate(LocalDate v){diaryDate=v;}
        public void setContentHtml(String v){contentHtml=v;}public void setMoodKey(String v){moodKey=v;}public void setSpacePublicId(byte[] v){spacePublicId=v;}
    }
    record MediaRow(byte[] publicId,String mediaType,String caption,LocalDateTime takenAt,int position){}
}

package com.langxi.babydiary.anniversary.infrastructure;

import com.langxi.babydiary.anniversary.application.AnniversaryRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AnniversaryMapper {
    @Select("""
            SELECT a.anniversary_id,a.public_id,s.public_id AS space_public_id,a.title,a.anniversary_date,
                   a.description,c.public_id AS cover_public_id,a.sort_order,a.created_at,a.updated_at
            FROM anniversary a JOIN diary_space s ON s.space_id=a.space_id
            LEFT JOIN media_asset c ON c.space_id=a.space_id AND c.asset_id=a.cover_asset_id AND c.deleted_at IS NULL
            WHERE a.space_id=#{spaceId} AND a.deleted_at IS NULL
            ORDER BY a.sort_order,a.anniversary_date,a.anniversary_id
            """)
    List<AnniversaryRow> findForSpace(long spaceId);

    @Select("""
            SELECT a.anniversary_id,a.public_id,s.public_id AS space_public_id,a.title,a.anniversary_date,
                   a.description,c.public_id AS cover_public_id,a.sort_order,a.created_at,a.updated_at
            FROM anniversary a JOIN diary_space s ON s.space_id=a.space_id
            LEFT JOIN media_asset c ON c.space_id=a.space_id AND c.asset_id=a.cover_asset_id AND c.deleted_at IS NULL
            WHERE a.space_id=#{spaceId} AND a.public_id=#{publicId} AND a.deleted_at IS NULL
            """)
    AnniversaryRow findByPublicId(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Insert("""
            INSERT INTO anniversary(public_id,space_id,created_by,title,anniversary_date,description,cover_asset_id,
              sort_order,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{createdBy},#{title},#{date},#{description},#{coverAssetId},#{sortOrder},
              UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "anniversaryId")
    void insert(AnniversaryInsert row);

    @Update("""
            UPDATE anniversary SET title=#{value.title},anniversary_date=#{value.date},description=#{value.description},
              cover_asset_id=#{value.coverAssetId},sort_order=#{value.sortOrder},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{publicId} AND deleted_at IS NULL
            """)
    int update(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
               @Param("value") AnniversaryRepository.UpdatedAnniversary value);

    @Update("""
            UPDATE anniversary SET deleted_at=#{deletedAt},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{publicId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
                   @Param("deletedAt") LocalDateTime deletedAt);

    final class AnniversaryInsert {
        private Long anniversaryId;
        private final byte[] publicId;
        private final long spaceId;
        private final long createdBy;
        private final String title;
        private final LocalDate date;
        private final String description;
        private final Long coverAssetId;
        private final int sortOrder;

        public AnniversaryInsert(byte[] publicId, long spaceId, long createdBy, String title, LocalDate date,
                                 String description, Long coverAssetId, int sortOrder) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.createdBy = createdBy;
            this.title = title;
            this.date = date;
            this.description = description;
            this.coverAssetId = coverAssetId;
            this.sortOrder = sortOrder;
        }

        public Long getAnniversaryId() { return anniversaryId; }
        public void setAnniversaryId(Long anniversaryId) { this.anniversaryId = anniversaryId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public long getCreatedBy() { return createdBy; }
        public String getTitle() { return title; }
        public LocalDate getDate() { return date; }
        public String getDescription() { return description; }
        public Long getCoverAssetId() { return coverAssetId; }
        public int getSortOrder() { return sortOrder; }
    }

    final class AnniversaryRow {
        private long anniversaryId;
        private byte[] publicId;
        private byte[] spacePublicId;
        private String title;
        private LocalDate anniversaryDate;
        private String description;
        private byte[] coverPublicId;
        private int sortOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public AnniversaryRow() {
        }

        public long anniversaryId() { return anniversaryId; }
        public byte[] publicId() { return publicId; }
        public byte[] spacePublicId() { return spacePublicId; }
        public String title() { return title; }
        public LocalDate anniversaryDate() { return anniversaryDate; }
        public String description() { return description; }
        public byte[] coverPublicId() { return coverPublicId; }
        public int sortOrder() { return sortOrder; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }

        public void setAnniversaryId(long anniversaryId) { this.anniversaryId = anniversaryId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setSpacePublicId(byte[] spacePublicId) { this.spacePublicId = spacePublicId; }
        public void setTitle(String title) { this.title = title; }
        public void setAnniversaryDate(LocalDate anniversaryDate) { this.anniversaryDate = anniversaryDate; }
        public void setDescription(String description) { this.description = description; }
        public void setCoverPublicId(byte[] coverPublicId) { this.coverPublicId = coverPublicId; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}

package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.MediaAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MediaMapper {
    void insertAsset(MediaAsset asset);

    void attachToDiary(@Param("diaryId") Integer diaryId, @Param("assetId") Long assetId, @Param("sort") int sort);

    int nextDiarySort(@Param("diaryId") Integer diaryId);

    MediaAsset findByPublicId(@Param("publicId") String publicId);

    MediaAsset findByIdForUpdate(@Param("assetId") Long assetId);

    List<MediaAsset> findByDiaryId(@Param("diaryId") Integer diaryId);

    List<MediaAsset> findByDiaryIds(@Param("diaryIds") List<Integer> diaryIds);

    int countAccessibleSharedLinks(@Param("assetId") Long assetId);

    int countLockedLinks(@Param("assetId") Long assetId);

    int countDiaryLinks(@Param("assetId") Long assetId);

    List<MediaAsset> findPending(@Param("limit") int limit);

    int updateProcessing(MediaAsset asset);

    int updateMetadata(@Param("assetId") Long assetId,
                       @Param("caption") String caption,
                       @Param("locationName") String locationName,
                       @Param("latitude") java.math.BigDecimal latitude,
                       @Param("longitude") java.math.BigDecimal longitude);

    long findUsedBytesForUpdate(@Param("spaceId") Long spaceId);

    long findQuotaBytes(@Param("spaceId") Long spaceId);

    int addUsedBytes(@Param("spaceId") Long spaceId, @Param("delta") long delta);

    int softDelete(@Param("assetId") Long assetId);
}

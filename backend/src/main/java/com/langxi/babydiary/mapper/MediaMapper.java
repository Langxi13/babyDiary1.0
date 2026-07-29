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

    int countDiaryInSpace(@Param("diaryId") Integer diaryId, @Param("spaceId") Long spaceId);

    MediaAsset findByPublicId(@Param("publicId") String publicId);

    List<MediaAsset> findByPublicIds(@Param("publicIds") List<String> publicIds);

    MediaAsset findById(@Param("assetId") Long assetId);

    Long findAssetIdByPublicId(@Param("publicId") String publicId);

    String findPublicIdByAssetId(@Param("assetId") Long assetId);

    MediaAsset findByIdForUpdate(@Param("assetId") Long assetId);

    List<MediaAsset> findByDiaryId(@Param("diaryId") Integer diaryId);

    List<MediaAsset> findByDiaryIds(@Param("diaryIds") List<Integer> diaryIds);

    List<MediaAsset> findByUserAndDateRange(@Param("userId") Integer userId,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

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

    int updateUsage(@Param("assetId") Long assetId, @Param("accessScope") String accessScope,
                    @Param("libraryVisible") boolean libraryVisible);

    long findUsedBytesForUpdate(@Param("spaceId") Long spaceId);

    long findQuotaBytes(@Param("spaceId") Long spaceId);

    int addUsedBytes(@Param("spaceId") Long spaceId, @Param("delta") long delta);

    int softDelete(@Param("assetId") Long assetId);

    void attachToDiaryIfMissing(@Param("diaryId") Integer diaryId, @Param("assetId") Long assetId, @Param("sort") int sort);

    int detachFromDiary(@Param("diaryId") Integer diaryId, @Param("assetId") Long assetId);

    int deleteDiaryMedia(@Param("diaryId") Integer diaryId);

    int updateDiaryMediaSort(@Param("diaryId") Integer diaryId, @Param("assetId") Long assetId, @Param("sort") int sort);

    int countAnyLinks(@Param("assetId") Long assetId);
}

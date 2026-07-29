package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.MediaAsset;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
public class MediaAssetVO {
    private String assetId;
    private String mediaType;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String checksumSha256;
    private Integer durationSeconds;
    private Integer width;
    private Integer height;
    private String caption;
    private String ocrText;
    private Timestamp takenAt;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;
    private String processingError;
    private String contentUrl;
    private String thumbnailUrl;
    private String posterUrl;
    private String waveformUrl;
    private String transcodedUrl;
    private Integer sort;
    private Boolean favorite;
    private Integer diaryId;
    private String diaryPublicId;
    private String diaryTitle;
    private String diaryDate;

    public static MediaAssetVO from(MediaAsset asset) {
        MediaAssetVO vo = new MediaAssetVO();
        vo.setAssetId(asset.getPublicId());
        vo.setMediaType(asset.getMediaType());
        vo.setOriginalFilename(asset.getOriginalFilename());
        vo.setContentType(asset.getContentType());
        vo.setSizeBytes(asset.getSizeBytes());
        vo.setChecksumSha256(asset.getChecksumSha256());
        vo.setDurationSeconds(asset.getDurationSeconds());
        vo.setWidth(asset.getWidth());
        vo.setHeight(asset.getHeight());
        vo.setCaption(asset.getCaption());
        vo.setOcrText(asset.getOcrText());
        vo.setTakenAt(asset.getTakenAt());
        vo.setLocationName(asset.getLocationName());
        vo.setLatitude(asset.getLatitude());
        vo.setLongitude(asset.getLongitude());
        vo.setStatus(asset.getStatus());
        vo.setProcessingError(asset.getProcessingError());
        vo.setSort(asset.getSort());
        vo.setFavorite(Boolean.TRUE.equals(asset.getFavorite()));
        vo.setDiaryId(asset.getDiaryId());
        vo.setDiaryPublicId(asset.getDiaryPublicId());
        vo.setDiaryTitle(asset.getDiaryTitle());
        vo.setDiaryDate(asset.getDiaryDate() == null ? null : asset.getDiaryDate().toString());
        return vo;
    }
}

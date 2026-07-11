package com.langxi.babydiary.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
public class MediaAsset {
    private Integer diaryId;
    private Long assetId;
    private String publicId;
    private Long spaceId;
    private Integer ownerUserId;
    private String mediaType;
    private String originalFilename;
    private String storageProvider;
    private String storageKey;
    private String thumbnailKey;
    private String posterKey;
    private String waveformKey;
    private String transcodedKey;
    private String contentType;
    private Long sizeBytes;
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
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;
    private Integer sort;
}

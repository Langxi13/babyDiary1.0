package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class Photo {
    private String assetId;
    private com.langxi.babydiary.dto.MediaAssetVO media;
    private Integer diaryId;
    private Integer userId;
    private Integer sort;
    private String diaryTitle;
    private Date diaryDate;
    private String moodKey;
    private Boolean favorite;
    private Timestamp createdAt;
}

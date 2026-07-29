package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class Anniversary {
    private Integer anniversaryId;
    private Integer userId;
    private Long spaceId;
    private String title;
    private Date date;
    private String description;
    private Long coverAssetId;
    private String coverAssetPublicId;
    private com.langxi.babydiary.dto.MediaAssetVO coverMedia;
    private Integer sort;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}

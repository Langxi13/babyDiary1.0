package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class PrivateShare {
    private Long shareId;
    private String publicId;
    private String tokenHash;
    private Long spaceId;
    private Integer diaryId;
    private Integer createdBy;
    private String passwordHash;
    private Timestamp expiresAt;
    private Integer maxViews;
    private Integer viewCount;
    private Timestamp revokedAt;
    private Timestamp createdAt;
}

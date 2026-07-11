package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class AppNotification {
    private Long notificationId;
    private String publicId;
    private Integer userId;
    private Long spaceId;
    private String type;
    private String title;
    private String body;
    private String targetPath;
    private String dedupeKey;
    private Timestamp readAt;
    private Timestamp createdAt;
}

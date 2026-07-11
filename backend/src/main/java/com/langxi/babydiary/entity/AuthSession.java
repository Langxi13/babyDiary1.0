package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class AuthSession {
    private Long sessionId;
    private String publicId;
    private Integer userId;
    private String refreshTokenHash;
    private String deviceName;
    private String userAgent;
    private String ipAddress;
    private Timestamp expiresAt;
    private Timestamp lastSeenAt;
    private Timestamp revokedAt;
    private Timestamp createdAt;
}

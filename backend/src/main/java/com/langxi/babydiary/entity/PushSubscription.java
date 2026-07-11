package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class PushSubscription {
    private Long subscriptionId;
    private Integer userId;
    private String endpointHash;
    private String endpoint;
    private String p256dh;
    private String authSecret;
    private String userAgent;
    private Timestamp createdAt;
    private Timestamp lastSuccessAt;
    private Timestamp revokedAt;
}

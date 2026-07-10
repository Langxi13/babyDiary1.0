package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class AiConfig {
    private Integer configId;
    private Boolean enabled;
    private String baseUrl;
    private String model;
    private String encryptedApiKey;
    private Integer timeoutSeconds;
    private Timestamp updatedAt;
}

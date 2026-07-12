package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SystemInvitationConfig {
    private Integer configId;
    private String encryptedCode;
    private Integer updatedBy;
    private Timestamp updatedAt;
}

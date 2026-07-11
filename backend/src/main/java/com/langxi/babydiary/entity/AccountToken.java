package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class AccountToken {
    private Long tokenId;
    private Integer userId;
    private String type;
    private String tokenHash;
    private Timestamp expiresAt;
    private Timestamp usedAt;
    private Timestamp createdAt;
}

package com.langxi.babydiary.entity;

import lombok.Data;
import java.sql.Timestamp;

@Data
public class User {
    private Integer userId;
    private String username;
    private String email;
    private Boolean emailVerified;
    private String password;
    private Timestamp createdAt;
    private String avatarAssetId;
    private com.langxi.babydiary.dto.MediaAssetVO avatarMedia;
    private Integer tokenVersion;
    private String systemRole;
    private String timezone;

    // Getters and Setters
}

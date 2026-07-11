package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SpaceMember {
    private Long spaceId;
    private Integer userId;
    private String role;
    private String status;
    private String username;
    private String avatarPath;
    private Timestamp joinedAt;
}

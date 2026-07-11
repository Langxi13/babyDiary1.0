package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryComment {
    private Long commentId;
    private String publicId;
    private Integer diaryId;
    private Integer userId;
    private String username;
    private String avatarPath;
    private String content;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;
}

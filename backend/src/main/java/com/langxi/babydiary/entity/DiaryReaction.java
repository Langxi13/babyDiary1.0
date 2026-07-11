package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryReaction {
    private Integer diaryId;
    private Integer userId;
    private String username;
    private String emoji;
    private Timestamp createdAt;
}

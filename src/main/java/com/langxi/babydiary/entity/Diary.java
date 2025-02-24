package com.langxi.babydiary.entity;

import lombok.Data;
import java.sql.Date;
import java.sql.Timestamp;

@Data
public class Diary {
    private Integer diaryId;
    private Integer userId;
    private String title;
    private Date date;
    private String content;
    private String imagePaths; // 存储多个图片路径（以逗号分隔）
    private Timestamp createdAt;
    // Getters and Setters
}

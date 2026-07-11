package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class AiReport {
    private Integer reportId;
    private Integer userId;
    private Long spaceId;
    private String scope;
    private String type;
    private String period;
    private Date periodStart;
    private Date periodEnd;
    private String title;
    private String contentMarkdown;
    private Integer diaryCount;
    private String model;
    private Timestamp createdAt;
}

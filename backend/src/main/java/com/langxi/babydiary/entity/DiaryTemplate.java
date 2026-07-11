package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryTemplate {
    private Long templateId;
    private String publicId;
    private Long spaceId;
    private Integer ownerUserId;
    private String templateKey;
    private String name;
    private String description;
    private String icon;
    private String promptText;
    private String contentHtml;
    private Boolean builtin;
    private Boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}

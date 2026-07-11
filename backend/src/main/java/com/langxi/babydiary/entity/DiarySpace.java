package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiarySpace {
    private Long spaceId;
    private String publicId;
    private String name;
    private String type;
    private Integer createdBy;
    private Integer personalOwnerId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String memberRole;
    private Integer memberCount;
}

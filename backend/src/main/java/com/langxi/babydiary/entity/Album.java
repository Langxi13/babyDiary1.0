package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Album {
    private Integer albumId;
    private Integer groupId;
    private Integer userId;
    private Long spaceId;
    private String name;
    private String description;
    private String type;
    private String coverImagePath;
    private Integer sort;
    private Integer photoCount;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}

package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class AlbumGroup {
    private Integer groupId;
    private Integer userId;
    private Long spaceId;
    private String name;
    private String type;
    private Integer sort;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}

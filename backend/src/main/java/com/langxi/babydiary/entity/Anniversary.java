package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class Anniversary {
    private Integer anniversaryId;
    private Integer userId;
    private String title;
    private Date date;
    private String description;
    private String coverImagePath;
    private Integer sort;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}

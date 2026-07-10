package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Tag {
    private Integer tagId;
    private Integer userId;
    private String name;
    private String color;
    private Timestamp createdAt;
}

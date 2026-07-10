package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class DiaryImage {
    private Integer imageId;
    private Integer diaryId;
    private String imagePath;
    private Integer sort;
}

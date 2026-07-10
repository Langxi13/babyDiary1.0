package com.langxi.babydiary.entity;

import lombok.Data;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

@Data
public class Diary {
    private Integer diaryId;
    private Integer userId;
    private String title;
    private Date date;
    private String content;
    private String moodKey;
    private String contentFormat;
    private List<String> imagePathList;
    private List<Tag> tagList;
    private Timestamp createdAt;
}

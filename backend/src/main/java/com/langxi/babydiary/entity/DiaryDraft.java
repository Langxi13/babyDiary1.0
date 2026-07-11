package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class DiaryDraft {
    private Integer draftId;
    private Integer userId;
    private Long spaceId;
    private String draftKey;
    private Integer diaryId;
    private String title;
    private Date date;
    private String content;
    private String contentFormat;
    private String moodKey;
    private String tagIds;
    private Timestamp updatedAt;
}

package com.langxi.babydiary.entity;

import lombok.Data;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

@Data
public class Diary {
    private Integer diaryId;
    private String publicId;
    private Integer userId;
    private String authorName;
    private Long spaceId;
    private String title;
    private Date date;
    private String content;
    private String moodKey;
    private String contentFormat;
    private String visibility;
    private Boolean locked;
    private Integer version;
    private List<String> imagePathList;
    private List<Tag> tagList;
    private List<com.langxi.babydiary.dto.MediaAssetVO> mediaList;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;
}

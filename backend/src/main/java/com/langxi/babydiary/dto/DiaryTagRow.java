package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Tag;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryTagRow {
    private Integer diaryId;
    private Integer tagId;
    private Integer userId;
    private String name;
    private String color;
    private Timestamp createdAt;

    public Tag toTag() {
        Tag tag = new Tag();
        tag.setTagId(tagId);
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(color);
        tag.setCreatedAt(createdAt);
        return tag;
    }
}

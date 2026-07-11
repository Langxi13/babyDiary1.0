package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryRevision {
    private Long revisionId;
    private Integer diaryId;
    private Integer version;
    private Integer editorUserId;
    private String editorName;
    private String snapshotJson;
    private Timestamp createdAt;
}

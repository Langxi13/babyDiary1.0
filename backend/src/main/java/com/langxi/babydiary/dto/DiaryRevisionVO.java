package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.DiaryRevision;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryRevisionVO {
    private Long revisionId;
    private Integer version;
    private Integer editorUserId;
    private String editorName;
    private Timestamp createdAt;

    public static DiaryRevisionVO from(DiaryRevision revision) {
        DiaryRevisionVO vo = new DiaryRevisionVO();
        vo.setRevisionId(revision.getRevisionId());
        vo.setVersion(revision.getVersion());
        vo.setEditorUserId(revision.getEditorUserId());
        vo.setEditorName(revision.getEditorName());
        vo.setCreatedAt(revision.getCreatedAt());
        return vo;
    }
}

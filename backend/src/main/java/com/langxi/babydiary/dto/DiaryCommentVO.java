package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.DiaryComment;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class DiaryCommentVO {
    private String publicId;
    private Integer userId;
    private String username;
    private String avatarPath;
    private String content;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public static DiaryCommentVO from(DiaryComment comment) {
        DiaryCommentVO vo = new DiaryCommentVO();
        vo.setPublicId(comment.getPublicId());
        vo.setUserId(comment.getUserId());
        vo.setUsername(comment.getUsername());
        vo.setAvatarPath(comment.getAvatarPath());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getUpdatedAt());
        return vo;
    }
}

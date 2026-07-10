package com.langxi.babydiary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
@Schema(description = "日记视图对象")
public class DiaryVO {

    @Schema(description = "日记ID")
    private Integer diaryId;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "日记标题")
    private String title;

    @Schema(description = "日记日期")
    private String date;

    @Schema(description = "日记内容")
    private String content;

    @Schema(description = "内容格式：plain/html")
    private String contentFormat;

    @Schema(description = "心情标识")
    private String moodKey;

    @Schema(description = "标签列表")
    private List<TagVO> tags;

    @Schema(description = "图片路径列表")
    private List<String> imagePathList;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    public static DiaryVO fromEntity(com.langxi.babydiary.entity.Diary diary) {
        DiaryVO vo = new DiaryVO();
        vo.setDiaryId(diary.getDiaryId());
        vo.setUserId(diary.getUserId());
        vo.setTitle(diary.getTitle());
        vo.setDate(diary.getDate() != null ? diary.getDate().toString() : null);
        vo.setContent(diary.getContent());
        vo.setContentFormat(diary.getContentFormat());
        vo.setMoodKey(diary.getMoodKey());
        if (diary.getTagList() != null) {
            vo.setTags(diary.getTagList().stream().map(TagVO::fromEntity).collect(java.util.stream.Collectors.toList()));
        }
        vo.setImagePathList(diary.getImagePathList());
        vo.setCreatedAt(diary.getCreatedAt());
        return vo;
    }
}

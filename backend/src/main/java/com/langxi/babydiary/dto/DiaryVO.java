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

    @Schema(description = "跨设备稳定ID")
    private String publicId;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "作者名称")
    private String authorName;

    @Schema(description = "所属空间ID")
    private Long spaceId;

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

    @Schema(description = "可见范围：PRIVATE/SHARED")
    private String visibility;

    @Schema(description = "是否需要二次验证")
    private Boolean locked;

    @Schema(description = "并发编辑版本")
    private Integer version;

    @Schema(description = "标签列表")
    private List<TagVO> tags;

    @Schema(description = "图片路径列表")
    private List<String> imagePathList;

    @Schema(description = "音视频及新对象存储媒体")
    private List<MediaAssetVO> media;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;

    public static DiaryVO fromEntity(com.langxi.babydiary.entity.Diary diary) {
        DiaryVO vo = new DiaryVO();
        vo.setDiaryId(diary.getDiaryId());
        vo.setPublicId(diary.getPublicId());
        vo.setUserId(diary.getUserId());
        vo.setAuthorName(diary.getAuthorName());
        vo.setSpaceId(diary.getSpaceId());
        vo.setTitle(diary.getTitle());
        vo.setDate(diary.getDate() != null ? diary.getDate().toString() : null);
        vo.setContent(diary.getContent());
        vo.setContentFormat(diary.getContentFormat());
        vo.setMoodKey(diary.getMoodKey());
        vo.setVisibility(diary.getVisibility());
        vo.setLocked(diary.getLocked());
        vo.setVersion(diary.getVersion());
        if (diary.getTagList() != null) {
            vo.setTags(diary.getTagList().stream().map(TagVO::fromEntity).collect(java.util.stream.Collectors.toList()));
        }
        vo.setImagePathList(diary.getImagePathList());
        vo.setMedia(diary.getMediaList());
        vo.setCreatedAt(diary.getCreatedAt());
        vo.setUpdatedAt(diary.getUpdatedAt());
        return vo;
    }
}

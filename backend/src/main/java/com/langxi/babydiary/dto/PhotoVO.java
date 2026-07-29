package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Photo;
import lombok.Data;

@Data
public class PhotoVO {
    private String assetId;
    private MediaAssetVO media;
    private Integer diaryId;
    private String diaryTitle;
    private String diaryDate;
    private String moodKey;
    private Boolean favorite;

    public static PhotoVO fromEntity(Photo photo) {
        PhotoVO vo = new PhotoVO();
        vo.setAssetId(photo.getAssetId());
        vo.setMedia(photo.getMedia());
        vo.setDiaryId(photo.getDiaryId());
        vo.setDiaryTitle(photo.getDiaryTitle());
        vo.setDiaryDate(photo.getDiaryDate() != null ? photo.getDiaryDate().toString() : null);
        vo.setMoodKey(photo.getMoodKey());
        vo.setFavorite(Boolean.TRUE.equals(photo.getFavorite()));
        return vo;
    }
}

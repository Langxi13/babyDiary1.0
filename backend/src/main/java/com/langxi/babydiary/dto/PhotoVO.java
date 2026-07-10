package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Photo;
import lombok.Data;

@Data
public class PhotoVO {
    private Integer imageId;
    private Integer diaryId;
    private String imagePath;
    private String diaryTitle;
    private String diaryDate;
    private String moodKey;
    private Boolean favorite;

    public static PhotoVO fromEntity(Photo photo) {
        PhotoVO vo = new PhotoVO();
        vo.setImageId(photo.getImageId());
        vo.setDiaryId(photo.getDiaryId());
        vo.setImagePath(photo.getImagePath());
        vo.setDiaryTitle(photo.getDiaryTitle());
        vo.setDiaryDate(photo.getDiaryDate() != null ? photo.getDiaryDate().toString() : null);
        vo.setMoodKey(photo.getMoodKey());
        vo.setFavorite(Boolean.TRUE.equals(photo.getFavorite()));
        return vo;
    }
}

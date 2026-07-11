package com.langxi.babydiary.dto;

import lombok.Data;

import java.util.List;

@Data
public class SharedDiaryVO {
    private String title;
    private String date;
    private String content;
    private String contentFormat;
    private String moodKey;
    private List<String> imagePathList;
    private List<MediaAssetVO> media;
}

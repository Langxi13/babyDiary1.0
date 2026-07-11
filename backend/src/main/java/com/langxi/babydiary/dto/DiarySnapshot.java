package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Diary;
import lombok.Data;

@Data
public class DiarySnapshot {
    private String title;
    private String date;
    private String content;
    private String contentFormat;
    private String moodKey;
    private String visibility;
    private Boolean locked;

    public static DiarySnapshot from(Diary diary) {
        DiarySnapshot snapshot = new DiarySnapshot();
        snapshot.setTitle(diary.getTitle());
        snapshot.setDate(diary.getDate() == null ? null : diary.getDate().toString());
        snapshot.setContent(diary.getContent());
        snapshot.setContentFormat(diary.getContentFormat());
        snapshot.setMoodKey(diary.getMoodKey());
        snapshot.setVisibility(diary.getVisibility());
        snapshot.setLocked(diary.getLocked());
        return snapshot;
    }
}

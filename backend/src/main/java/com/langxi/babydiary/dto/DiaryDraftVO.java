package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.DiaryDraft;
import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DiaryDraftVO {
    private Integer draftId;
    private String draftKey;
    private Integer diaryId;
    private String title;
    private String date;
    private String content;
    private String contentFormat;
    private String moodKey;
    private List<Integer> tagIds;
    private Long updatedAt;

    public static DiaryDraftVO fromEntity(DiaryDraft draft) {
        DiaryDraftVO vo = new DiaryDraftVO();
        vo.setDraftId(draft.getDraftId());
        vo.setDraftKey(draft.getDraftKey());
        vo.setDiaryId(draft.getDiaryId());
        vo.setTitle(draft.getTitle());
        vo.setDate(draft.getDate() != null ? draft.getDate().toString() : null);
        vo.setContent(draft.getContent());
        vo.setContentFormat(draft.getContentFormat());
        vo.setMoodKey(draft.getMoodKey());
        if (draft.getTagIds() == null || draft.getTagIds().trim().isEmpty()) {
            vo.setTagIds(Collections.emptyList());
        } else {
            vo.setTagIds(Arrays.stream(draft.getTagIds().split(","))
                    .filter(item -> !item.trim().isEmpty())
                    .map(Integer::valueOf)
                    .collect(Collectors.toList()));
        }
        vo.setUpdatedAt(draft.getUpdatedAt() != null ? draft.getUpdatedAt().getTime() : null);
        return vo;
    }
}

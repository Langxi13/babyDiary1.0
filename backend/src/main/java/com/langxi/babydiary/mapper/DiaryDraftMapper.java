package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.DiaryDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiaryDraftMapper {
    List<DiaryDraft> findDraftsByUserId(@Param("userId") Integer userId);

    DiaryDraft findByDraftKey(@Param("userId") Integer userId, @Param("draftKey") String draftKey);

    void upsertDraft(DiaryDraft draft);

    void deleteDraft(@Param("userId") Integer userId, @Param("draftId") Integer draftId);

    void deleteDraftByKey(@Param("userId") Integer userId, @Param("draftKey") String draftKey);
}

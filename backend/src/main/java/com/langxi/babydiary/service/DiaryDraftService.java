package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.dto.DiaryDraftDTO;
import com.langxi.babydiary.entity.DiaryDraft;
import com.langxi.babydiary.mapper.DiaryDraftMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiaryDraftService {
    @Autowired
    private DiaryDraftMapper diaryDraftMapper;

    @Autowired
    private HtmlSanitizer htmlSanitizer;

    @Cacheable(cacheNames = CacheNames.DRAFTS, key = "'list:' + #userId")
    public List<DiaryDraft> findDrafts(Integer userId) {
        return diaryDraftMapper.findDraftsByUserId(userId);
    }

    @Cacheable(cacheNames = CacheNames.DRAFTS, key = "'draft:' + #userId + ':' + #draftKey")
    public DiaryDraft findByDraftKey(Integer userId, String draftKey) {
        return diaryDraftMapper.findByDraftKey(userId, draftKey);
    }

    @CacheEvict(cacheNames = CacheNames.DRAFTS, allEntries = true)
    public DiaryDraft saveDraft(Integer userId, DiaryDraftDTO dto) {
        DiaryDraft draft = new DiaryDraft();
        draft.setUserId(userId);
        draft.setDraftKey(dto.getDraftKey() == null || dto.getDraftKey().trim().isEmpty() ? "create" : dto.getDraftKey().trim());
        draft.setDiaryId(dto.getDiaryId());
        draft.setTitle(dto.getTitle());
        draft.setDate(dto.getDate() == null || dto.getDate().trim().isEmpty() ? null : Date.valueOf(dto.getDate()));
        String format = dto.getContentFormat() == null ? "html" : dto.getContentFormat();
        draft.setContentFormat(format);
        draft.setContent("html".equals(format) ? htmlSanitizer.sanitize(dto.getContent()) : dto.getContent());
        draft.setMoodKey(dto.getMoodKey());
        draft.setTagIds(dto.getTagIds() == null ? null : dto.getTagIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        diaryDraftMapper.upsertDraft(draft);
        return diaryDraftMapper.findByDraftKey(userId, draft.getDraftKey());
    }

    @CacheEvict(cacheNames = CacheNames.DRAFTS, allEntries = true)
    public void deleteDraft(Integer userId, Integer draftId) {
        diaryDraftMapper.deleteDraft(userId, draftId);
    }

    @CacheEvict(cacheNames = CacheNames.DRAFTS, allEntries = true)
    public void deleteDraftByKey(Integer userId, String draftKey) {
        diaryDraftMapper.deleteDraftByKey(userId, draftKey);
    }
}

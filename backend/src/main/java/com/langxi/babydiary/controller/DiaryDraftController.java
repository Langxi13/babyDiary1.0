package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.DiaryDraftDTO;
import com.langxi.babydiary.dto.DiaryDraftVO;
import com.langxi.babydiary.entity.DiaryDraft;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.DiaryDraftService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/diary-drafts")
public class DiaryDraftController {
    @Autowired
    private DiaryDraftService diaryDraftService;

    @Autowired
    private CurrentUser currentUser;

    @GetMapping
    public Result<List<DiaryDraftVO>> listDrafts() {
        List<DiaryDraftVO> drafts = diaryDraftService.findDrafts(currentUser.getUserId())
                .stream()
                .map(DiaryDraftVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(drafts);
    }

    @GetMapping("/{draftKey}")
    public Result<DiaryDraftVO> getDraft(@PathVariable String draftKey) {
        DiaryDraft draft = diaryDraftService.findByDraftKey(currentUser.getUserId(), draftKey);
        return Result.success(draft == null ? null : DiaryDraftVO.fromEntity(draft));
    }

    @PutMapping
    public Result<DiaryDraftVO> saveDraft(@Valid @RequestBody DiaryDraftDTO dto) {
        return Result.success("草稿已保存", DiaryDraftVO.fromEntity(diaryDraftService.saveDraft(currentUser.getUserId(), dto)));
    }

    @DeleteMapping("/{draftId}")
    public Result<Void> deleteDraft(@PathVariable Integer draftId) {
        diaryDraftService.deleteDraft(currentUser.getUserId(), draftId);
        return Result.success("草稿已删除", null);
    }

    @DeleteMapping("/key/{draftKey}")
    public Result<Void> deleteDraftByKey(@PathVariable String draftKey) {
        diaryDraftService.deleteDraftByKey(currentUser.getUserId(), draftKey);
        return Result.success("草稿已删除", null);
    }
}

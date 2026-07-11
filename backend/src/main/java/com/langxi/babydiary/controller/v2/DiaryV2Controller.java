package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.CollaborativeDiaryService;
import com.langxi.babydiary.service.DiaryInteractionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/diaries")
public class DiaryV2Controller {
    private final CollaborativeDiaryService diaryService;
    private final DiaryInteractionService interactionService;
    private final CurrentUser currentUser;

    public DiaryV2Controller(CollaborativeDiaryService diaryService,
                             DiaryInteractionService interactionService,
                             CurrentUser currentUser) {
        this.diaryService = diaryService;
        this.interactionService = interactionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Result<PageResult<DiaryVO>> list(@PathVariable String spaceId,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "false") boolean trash,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.success(diaryService.list(spaceId, currentUser.getUserId(), startDate, endDate,
                keyword, trash, page, size));
    }

    @GetMapping("/{diaryId}")
    public ResponseEntity<Result<DiaryVO>> detail(@PathVariable String spaceId,
                                                   @PathVariable String diaryId,
                                                   @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        DiaryVO diary = diaryService.get(spaceId, diaryId, currentUser.getUserId(), stepUpToken);
        return withEtag(Result.success(diary), diary.getVersion());
    }

    @PostMapping
    public ResponseEntity<Result<DiaryVO>> create(@PathVariable String spaceId,
                                                   @Valid @RequestBody DiaryWriteDTO dto,
                                                   @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        DiaryVO diary = diaryService.create(spaceId, currentUser.getUserId(), dto, stepUpToken);
        return withEtag(Result.success("日记已创建", diary), diary.getVersion());
    }

    @PutMapping("/{diaryId}")
    public ResponseEntity<Result<DiaryVO>> update(@PathVariable String spaceId,
                                                   @PathVariable String diaryId,
                                                   @Valid @RequestBody DiaryWriteDTO dto,
                                                   @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                   @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        Integer version = expectedVersion(ifMatch, dto.getBaseVersion());
        DiaryVO diary = diaryService.update(spaceId, diaryId, currentUser.getUserId(), dto, version, stepUpToken);
        return withEtag(Result.success("日记已更新", diary), diary.getVersion());
    }

    @DeleteMapping("/{diaryId}")
    public Result<Void> delete(@PathVariable String spaceId,
                               @PathVariable String diaryId,
                               @RequestHeader(value = "If-Match", required = false) String ifMatch,
                               @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        diaryService.moveToTrash(spaceId, diaryId, currentUser.getUserId(), expectedVersion(ifMatch, null), stepUpToken);
        return Result.success("日记已移入回收站，30天后自动清理", null);
    }

    @PostMapping("/{diaryId}/restore")
    public ResponseEntity<Result<DiaryVO>> restore(@PathVariable String spaceId,
                                                    @PathVariable String diaryId,
                                                    @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                    @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        DiaryVO diary = diaryService.restore(spaceId, diaryId, currentUser.getUserId(),
                expectedVersion(ifMatch, null), stepUpToken);
        return withEtag(Result.success("日记已恢复", diary), diary.getVersion());
    }

    @GetMapping("/{diaryId}/revisions")
    public Result<List<DiaryRevisionVO>> revisions(@PathVariable String spaceId,
                                                    @PathVariable String diaryId,
                                                    @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success(diaryService.revisions(spaceId, diaryId, currentUser.getUserId(), stepUpToken));
    }

    @PostMapping("/{diaryId}/revisions/{revisionId}/restore")
    public ResponseEntity<Result<DiaryVO>> restoreRevision(@PathVariable String spaceId,
                                                            @PathVariable String diaryId,
                                                            @PathVariable Long revisionId,
                                                            @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        DiaryVO diary = diaryService.restoreRevision(spaceId, diaryId, revisionId, currentUser.getUserId(),
                expectedVersion(ifMatch, null), stepUpToken);
        return withEtag(Result.success("历史版本已恢复", diary), diary.getVersion());
    }

    @GetMapping("/{diaryId}/comments")
    public Result<List<DiaryCommentVO>> comments(@PathVariable String spaceId,
                                                  @PathVariable String diaryId,
                                                  @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success(interactionService.comments(spaceId, diaryId, currentUser.getUserId(), stepUpToken));
    }

    @PostMapping("/{diaryId}/comments")
    public Result<DiaryCommentVO> addComment(@PathVariable String spaceId,
                                              @PathVariable String diaryId,
                                              @Valid @RequestBody DiaryCommentDTO dto,
                                              @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success("评论已发布", interactionService.addComment(
                spaceId, diaryId, currentUser.getUserId(), dto.getContent(), stepUpToken));
    }

    @PutMapping("/{diaryId}/comments/{commentId}")
    public Result<Void> updateComment(@PathVariable String spaceId,
                                      @PathVariable String diaryId,
                                      @PathVariable String commentId,
                                      @Valid @RequestBody DiaryCommentDTO dto,
                                      @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        interactionService.updateComment(spaceId, diaryId, commentId, currentUser.getUserId(), dto.getContent(), stepUpToken);
        return Result.success("评论已更新", null);
    }

    @DeleteMapping("/{diaryId}/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable String spaceId,
                                      @PathVariable String diaryId,
                                      @PathVariable String commentId,
                                      @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        interactionService.deleteComment(spaceId, diaryId, commentId, currentUser.getUserId(), stepUpToken);
        return Result.success("评论已删除", null);
    }

    @GetMapping("/{diaryId}/reactions")
    public Result<List<ReactionSummaryVO>> reactions(@PathVariable String spaceId,
                                                      @PathVariable String diaryId,
                                                      @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success(interactionService.reactions(spaceId, diaryId, currentUser.getUserId(), stepUpToken));
    }

    @PutMapping("/{diaryId}/reactions")
    public Result<Void> reaction(@PathVariable String spaceId,
                                 @PathVariable String diaryId,
                                 @Valid @RequestBody ReactionDTO dto,
                                 @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        interactionService.setReaction(spaceId, diaryId, currentUser.getUserId(), dto.getEmoji(), dto.isActive(), stepUpToken);
        return Result.success(dto.isActive() ? "回应已添加" : "回应已取消", null);
    }

    private Integer expectedVersion(String ifMatch, Integer bodyVersion) {
        if (bodyVersion != null) return bodyVersion;
        if (ifMatch == null || ifMatch.isBlank()) return null;
        String normalized = ifMatch.trim().replace("W/", "").replace("\"", "");
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ResponseEntity<Result<DiaryVO>> withEtag(Result<DiaryVO> result, Integer version) {
        return ResponseEntity.ok().eTag("\"" + version + "\"").body(result);
    }
}

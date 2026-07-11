package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.DiaryCommentVO;
import com.langxi.babydiary.dto.ReactionSummaryVO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiaryComment;
import com.langxi.babydiary.entity.DiaryReaction;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DiaryInteractionService {
    private static final Set<String> ALLOWED_REACTIONS = Set.of("❤️", "👍", "🥰", "😂", "🎉", "🤗");

    private final CollaborationMapper mapper;
    private final SpaceService spaceService;
    private final AccountSecurityService accountSecurityService;
    private final NotificationService notificationService;

    public DiaryInteractionService(CollaborationMapper mapper,
                                   SpaceService spaceService,
                                   AccountSecurityService accountSecurityService,
                                   NotificationService notificationService) {
        this.mapper = mapper;
        this.spaceService = spaceService;
        this.accountSecurityService = accountSecurityService;
        this.notificationService = notificationService;
    }

    public List<DiaryCommentVO> comments(String spacePublicId, String diaryPublicId, Integer userId,
                                         String stepUpToken) {
        Diary diary = requireAccessible(spacePublicId, diaryPublicId, userId, stepUpToken);
        return mapper.findComments(diary.getDiaryId()).stream().map(DiaryCommentVO::from).toList();
    }

    @Transactional
    public DiaryCommentVO addComment(String spacePublicId, String diaryPublicId, Integer userId,
                                     String content, String stepUpToken) {
        Diary diary = requireAccessible(spacePublicId, diaryPublicId, userId, stepUpToken);
        DiaryComment comment = new DiaryComment();
        comment.setPublicId(UUID.randomUUID().toString());
        comment.setDiaryId(diary.getDiaryId());
        comment.setUserId(userId);
        comment.setContent(content.trim());
        mapper.insertComment(comment);
        if ("SHARED".equals(diary.getVisibility())) {
            notificationService.notifySpaceMembers(diary.getSpaceId(), userId, "DIARY_COMMENT", "共同日记有新评论",
                    comment.getContent(), diaryTarget(spacePublicId, diaryPublicId), comment.getPublicId());
        }
        return mapper.findComments(diary.getDiaryId()).stream()
                .filter(item -> item.getPublicId().equals(comment.getPublicId()))
                .findFirst().map(DiaryCommentVO::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    @Transactional
    public void updateComment(String spacePublicId, String diaryPublicId, String commentPublicId,
                              Integer userId, String content, String stepUpToken) {
        Diary diary = requireAccessible(spacePublicId, diaryPublicId, userId, stepUpToken);
        DiaryComment comment = mapper.findComment(diary.getDiaryId(), commentPublicId);
        if (comment == null) throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        if (mapper.updateComment(comment.getCommentId(), userId, content.trim()) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional
    public void deleteComment(String spacePublicId, String diaryPublicId, String commentPublicId,
                              Integer userId, String stepUpToken) {
        Diary diary = requireAccessible(spacePublicId, diaryPublicId, userId, stepUpToken);
        DiaryComment comment = mapper.findComment(diary.getDiaryId(), commentPublicId);
        if (comment == null) throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        if (mapper.deleteComment(comment.getCommentId(), userId) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public List<ReactionSummaryVO> reactions(String spacePublicId, String diaryPublicId, Integer userId,
                                             String stepUpToken) {
        Diary diary = requireAccessible(spacePublicId, diaryPublicId, userId, stepUpToken);
        Map<String, List<DiaryReaction>> grouped = mapper.findReactions(diary.getDiaryId()).stream()
                .collect(Collectors.groupingBy(DiaryReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream().map(entry -> new ReactionSummaryVO(
                entry.getKey(), entry.getValue().size(),
                entry.getValue().stream().anyMatch(reaction -> reaction.getUserId().equals(userId)),
                entry.getValue().stream().map(DiaryReaction::getUsername).distinct().toList())).toList();
    }

    @Transactional
    public void setReaction(String spacePublicId, String diaryPublicId, Integer userId,
                            String emoji, boolean active, String stepUpToken) {
        if (!ALLOWED_REACTIONS.contains(emoji)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持该回应");
        }
        Diary diary = requireAccessible(spacePublicId, diaryPublicId, userId, stepUpToken);
        if (active) mapper.addReaction(diary.getDiaryId(), userId, emoji);
        else mapper.removeReaction(diary.getDiaryId(), userId, emoji);
        if (active && "SHARED".equals(diary.getVisibility())) {
            notificationService.notifySpaceMembers(diary.getSpaceId(), userId, "DIARY_REACTION", "共同日记收到新回应",
                    emoji, diaryTarget(spacePublicId, diaryPublicId), diaryPublicId + ":" + userId + ":" + emoji);
        }
    }

    private Diary requireAccessible(String spacePublicId, String diaryPublicId, Integer userId, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary diary = mapper.findDiary(space.getSpaceId(), diaryPublicId);
        if (diary == null || diary.getDeletedAt() != null
                || ("PRIVATE".equals(diary.getVisibility()) && !diary.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(diary.getLocked())) {
            accountSecurityService.requireStepUp(userId, stepUpToken);
        }
        return diary;
    }

    private String diaryTarget(String spacePublicId, String diaryPublicId) {
        return "/spaces/" + spacePublicId + "/diaries/" + diaryPublicId;
    }
}

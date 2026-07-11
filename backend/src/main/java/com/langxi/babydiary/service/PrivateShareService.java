package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.PrivateShare;
import com.langxi.babydiary.entity.SpaceMember;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.PrivateShareMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class PrivateShareService {
    private final PrivateShareMapper shareMapper;
    private final CollaborationMapper diaryMapper;
    private final SpaceService spaceService;
    private final DiaryImageService imageService;
    private final AccountSecurityService accountSecurityService;
    private final MediaService mediaService;
    private final PasswordEncoder passwordEncoder;

    public PrivateShareService(PrivateShareMapper shareMapper,
                               CollaborationMapper diaryMapper,
                               SpaceService spaceService,
                               DiaryImageService imageService,
                               AccountSecurityService accountSecurityService,
                               MediaService mediaService,
                               PasswordEncoder passwordEncoder) {
        this.shareMapper = shareMapper;
        this.diaryMapper = diaryMapper;
        this.spaceService = spaceService;
        this.imageService = imageService;
        this.accountSecurityService = accountSecurityService;
        this.mediaService = mediaService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public PrivateShareVO create(String spacePublicId, String diaryPublicId, Integer userId,
                                 PrivateShareCreateDTO dto, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        Diary diary = requireManageableDiary(space, diaryPublicId, userId, stepUpToken);
        String rawToken = SecureTokens.randomToken(32);
        PrivateShare share = new PrivateShare();
        share.setPublicId(UUID.randomUUID().toString());
        share.setTokenHash(SecureTokens.sha256(rawToken));
        share.setSpaceId(space.getSpaceId());
        share.setDiaryId(diary.getDiaryId());
        share.setCreatedBy(userId);
        share.setPasswordHash(dto.getPassword() == null || dto.getPassword().isBlank()
                ? null : passwordEncoder.encode(dto.getPassword()));
        share.setExpiresAt(Timestamp.from(Instant.now().plus(dto.getExpiresInHours(), ChronoUnit.HOURS)));
        share.setMaxViews(dto.getMaxViews());
        shareMapper.insert(share);
        return new PrivateShareVO(share.getPublicId(), "/shared/" + rawToken, share.getExpiresAt(), share.getMaxViews());
    }

    public List<PrivateShareSummaryVO> list(String spacePublicId, String diaryPublicId, Integer userId,
                                            String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        Diary diary = requireManageableDiary(space, diaryPublicId, userId, stepUpToken);
        return shareMapper.findActiveByDiary(diary.getDiaryId(), userId).stream()
                .map(PrivateShareSummaryVO::from)
                .toList();
    }

    @Transactional
    public SharedDiaryVO open(String rawToken, String password) {
        PrivateShare share = shareMapper.findByTokenForUpdate(SecureTokens.sha256(rawToken));
        if (share == null || share.getRevokedAt() != null || share.getExpiresAt().before(Timestamp.from(Instant.now()))
                || (share.getMaxViews() != null && share.getViewCount() >= share.getMaxViews())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享不存在或已过期");
        }
        if (share.getPasswordHash() != null
                && (password == null || !passwordEncoder.matches(password, share.getPasswordHash()))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "分享密码不正确");
        }
        if (shareMapper.incrementView(share.getShareId()) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享不存在或已过期");
        }
        Diary diary = diaryMapper.findDiaryByIdForShare(share.getDiaryId());
        if (diary == null || diary.getDeletedAt() != null) throw new BusinessException(ErrorCode.NOT_FOUND, "日记已不可用");
        SharedDiaryVO vo = new SharedDiaryVO();
        vo.setTitle(diary.getTitle());
        vo.setDate(diary.getDate().toString());
        vo.setContent(diary.getContent());
        vo.setContentFormat(diary.getContentFormat());
        vo.setMoodKey(diary.getMoodKey());
        vo.setImagePathList(imageService.findImagePathsByDiaryId(diary.getDiaryId()));
        vo.setMedia(mediaService.findByDiary(diary.getDiaryId()));
        return vo;
    }

    public void revoke(String shareId, Integer userId) {
        if (shareMapper.revoke(shareId, userId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享不存在");
        }
    }

    private Diary requireManageableDiary(DiarySpace space, String diaryPublicId, Integer userId,
                                         String stepUpToken) {
        SpaceMember member = spaceService.requireMember(space, userId);
        Diary diary = diaryMapper.findDiary(space.getSpaceId(), diaryPublicId);
        if (diary == null || diary.getDeletedAt() != null
                || ("PRIVATE".equals(diary.getVisibility()) && !diary.getUserId().equals(userId))) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        if (!diary.getUserId().equals(userId) && !"OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有作者或空间所有者可以管理分享");
        }
        if (Boolean.TRUE.equals(diary.getLocked())) accountSecurityService.requireStepUp(userId, stepUpToken);
        return diary;
    }
}

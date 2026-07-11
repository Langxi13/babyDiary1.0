package com.langxi.babydiary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.entity.*;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.langxi.babydiary.common.CacheNames.*;

@Service
public class CollaborativeDiaryService {
    private final CollaborationMapper mapper;
    private final SpaceService spaceService;
    private final TagService tagService;
    private final DiaryImageService diaryImageService;
    private final DiaryImageMapper diaryImageMapper;
    private final HtmlSanitizer htmlSanitizer;
    private final AccountSecurityService accountSecurityService;
    private final ImageStorageService imageStorageService;
    private final NotificationService notificationService;
    private final SyncJournalService syncJournalService;
    private final SearchService searchService;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    public CollaborativeDiaryService(CollaborationMapper mapper,
                                     SpaceService spaceService,
                                     TagService tagService,
                                     DiaryImageService diaryImageService,
                                     DiaryImageMapper diaryImageMapper,
                                     HtmlSanitizer htmlSanitizer,
                                     AccountSecurityService accountSecurityService,
                                     ImageStorageService imageStorageService,
                                     NotificationService notificationService,
                                     SyncJournalService syncJournalService,
                                     SearchService searchService,
                                     MediaService mediaService,
                                     ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.spaceService = spaceService;
        this.tagService = tagService;
        this.diaryImageService = diaryImageService;
        this.diaryImageMapper = diaryImageMapper;
        this.htmlSanitizer = htmlSanitizer;
        this.accountSecurityService = accountSecurityService;
        this.imageStorageService = imageStorageService;
        this.notificationService = notificationService;
        this.syncJournalService = syncJournalService;
        this.searchService = searchService;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    public PageResult<DiaryVO> list(String spacePublicId, Integer userId, String startDate, String endDate,
                                    String keyword, boolean trash, int page, int size) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        int total = mapper.countDiaries(space.getSpaceId(), userId, startDate, endDate, normalizedKeyword, trash);
        List<Diary> diaries = mapper.findDiaryPage(space.getSpaceId(), userId, startDate, endDate,
                normalizedKeyword, trash, normalizedSize, Pagination.offset(normalizedPage, normalizedSize));
        enrich(diaries);
        List<DiaryVO> content = diaries.stream().map(diary -> {
            DiaryVO vo = DiaryVO.fromEntity(diary);
            if (Boolean.TRUE.equals(diary.getLocked())) {
                vo.setTitle("已锁定的日记");
                vo.setContent("");
                vo.setMoodKey(null);
                vo.setTags(Collections.emptyList());
                vo.setImagePathList(Collections.emptyList());
                vo.setMedia(Collections.emptyList());
            }
            return vo;
        }).toList();
        return new PageResult<>(content, normalizedPage, normalizedSize, (long) total);
    }

    public DiaryVO get(String spacePublicId, String diaryPublicId, Integer userId, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary diary = requireVisibleDiary(space, diaryPublicId, userId, false);
        requireUnlocked(diary, userId, stepUpToken);
        enrich(diary);
        return DiaryVO.fromEntity(diary);
    }

    @Transactional
    @CacheEvict(cacheNames = {DIARY_PAGE, DIARY_TIMELINE, DIARY_CALENDAR, PHOTOS}, allEntries = true)
    public DiaryVO create(String spacePublicId, Integer userId, DiaryWriteDTO dto, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary diary = new Diary();
        diary.setPublicId(dto.getClientId() == null
                ? UUID.randomUUID().toString()
                : UUID.fromString(dto.getClientId()).toString());
        diary.setUserId(userId);
        diary.setSpaceId(space.getSpaceId());
        applyInput(diary, dto, "SHARED".equals(space.getType()) ? "SHARED" : "PRIVATE", false);
        if (Boolean.TRUE.equals(diary.getLocked())) {
            accountSecurityService.requireStepUp(userId, stepUpToken);
        }
        mapper.insertDiary(diary);
        diary.setVersion(1);
        tagService.replaceDiaryTags(userId, space.getSpaceId(), diary.getDiaryId(), dto.getTagIds());
        searchService.indexDiary(diary.getDiaryId());
        DiaryVO created = get(spacePublicId, diary.getPublicId(), userId, stepUpToken);
        syncJournalService.recordDiary(space.getSpaceId(), userId, "CREATE", created);
        if ("SHARED".equals(diary.getVisibility())) {
            notificationService.notifySpaceMembers(space.getSpaceId(), userId, "DIARY_CREATED", "空间里有一篇新日记",
                    notificationPreview(diary), diaryTarget(spacePublicId, diary.getPublicId()), diary.getPublicId() + ":1");
        }
        return created;
    }

    @Transactional
    @CacheEvict(cacheNames = {DIARY_PAGE, DIARY_TIMELINE, DIARY_CALENDAR, PHOTOS}, allEntries = true)
    public DiaryVO update(String spacePublicId, String diaryPublicId, Integer userId, DiaryWriteDTO dto,
                          Integer expectedVersion, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary current = requireForUpdate(space, diaryPublicId, userId, false);
        requireUnlocked(current, userId, stepUpToken);
        requireVersion(current, expectedVersion);
        requireEditPermission(current, userId);
        String nextVisibility = dto.getVisibility() == null ? current.getVisibility() : dto.getVisibility();
        if (!current.getUserId().equals(userId) && "PRIVATE".equals(nextVisibility)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有日记作者可以将日记设为私密");
        }
        if (!current.getUserId().equals(userId)
                && dto.getLocked() != null
                && !Objects.equals(dto.getLocked(), current.getLocked())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有日记作者可以修改日记锁");
        }
        saveRevision(current, userId);
        Diary next = new Diary();
        next.setDiaryId(current.getDiaryId());
        next.setPublicId(current.getPublicId());
        next.setUserId(current.getUserId());
        next.setSpaceId(current.getSpaceId());
        applyInput(next, dto, current.getVisibility(), Boolean.TRUE.equals(current.getLocked()));
        if (mapper.updateDiary(next, expectedVersion) != 1) {
            throw new BusinessException(ErrorCode.DIARY_VERSION_CONFLICT);
        }
        tagService.replaceDiaryTags(userId, space.getSpaceId(), current.getDiaryId(), dto.getTagIds());
        searchService.indexDiary(current.getDiaryId());
        DiaryVO updated = get(spacePublicId, diaryPublicId, userId, stepUpToken);
        syncJournalService.recordDiary(space.getSpaceId(), userId, "UPDATE", updated);
        if ("SHARED".equals(next.getVisibility())) {
            notificationService.notifySpaceMembers(space.getSpaceId(), userId, "DIARY_UPDATED", "共同日记已更新",
                    notificationPreview(next), diaryTarget(spacePublicId, diaryPublicId), diaryPublicId + ":" + (expectedVersion + 1));
        }
        return updated;
    }

    @Transactional
    @CacheEvict(cacheNames = {DIARY_PAGE, DIARY_TIMELINE, DIARY_CALENDAR, PHOTOS}, allEntries = true)
    public void moveToTrash(String spacePublicId, String diaryPublicId, Integer userId,
                            Integer expectedVersion, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        SpaceMember member = spaceService.requireMember(space, userId);
        Diary current = requireForUpdate(space, diaryPublicId, userId, false);
        requireUnlocked(current, userId, stepUpToken);
        requireVersion(current, expectedVersion);
        requireDeletePermission(current, member, userId);
        saveRevision(current, userId);
        if (mapper.softDelete(current.getDiaryId(), expectedVersion) != 1) {
            throw new BusinessException(ErrorCode.DIARY_VERSION_CONFLICT);
        }
        syncJournalService.recordDiaryDeletion(space.getSpaceId(), userId, diaryPublicId, expectedVersion + 1);
        searchService.removeDiary(diaryPublicId);
    }

    @Transactional
    @CacheEvict(cacheNames = {DIARY_PAGE, DIARY_TIMELINE, DIARY_CALENDAR, PHOTOS}, allEntries = true)
    public DiaryVO restore(String spacePublicId, String diaryPublicId, Integer userId, Integer expectedVersion,
                           String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        SpaceMember member = spaceService.requireMember(space, userId);
        Diary current = requireForUpdate(space, diaryPublicId, userId, true);
        requireUnlocked(current, userId, stepUpToken);
        requireVersion(current, expectedVersion);
        requireDeletePermission(current, member, userId);
        saveRevision(current, userId);
        if (mapper.restore(current.getDiaryId(), expectedVersion) != 1) {
            throw new BusinessException(ErrorCode.DIARY_VERSION_CONFLICT);
        }
        DiaryVO restored = get(spacePublicId, diaryPublicId, userId, stepUpToken);
        searchService.indexDiary(current.getDiaryId());
        syncJournalService.recordDiary(space.getSpaceId(), userId, "RESTORE", restored);
        return restored;
    }

    public List<DiaryRevisionVO> revisions(String spacePublicId, String diaryPublicId, Integer userId,
                                           String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary diary = requireVisibleDiary(space, diaryPublicId, userId, true);
        requireUnlocked(diary, userId, stepUpToken);
        return mapper.findRevisions(diary.getDiaryId()).stream().map(DiaryRevisionVO::from).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {DIARY_PAGE, DIARY_TIMELINE, DIARY_CALENDAR, PHOTOS}, allEntries = true)
    public DiaryVO restoreRevision(String spacePublicId, String diaryPublicId, Long revisionId,
                                   Integer userId, Integer expectedVersion, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary current = requireForUpdate(space, diaryPublicId, userId, false);
        requireUnlocked(current, userId, stepUpToken);
        requireEditPermission(current, userId);
        requireVersion(current, expectedVersion);
        DiaryRevision revision = mapper.findRevision(current.getDiaryId(), revisionId);
        if (revision == null) throw new BusinessException(ErrorCode.NOT_FOUND, "历史版本不存在");
        DiarySnapshot snapshot = readSnapshot(revision.getSnapshotJson());
        saveRevision(current, userId);
        Diary restored = new Diary();
        restored.setDiaryId(current.getDiaryId());
        restored.setTitle(snapshot.getTitle());
        restored.setDate(Date.valueOf(snapshot.getDate()));
        restored.setContent(snapshot.getContent());
        restored.setContentFormat(snapshot.getContentFormat());
        restored.setMoodKey(snapshot.getMoodKey());
        restored.setVisibility(snapshot.getVisibility());
        restored.setLocked(snapshot.getLocked());
        if (mapper.restoreSnapshot(restored, expectedVersion) != 1) {
            throw new BusinessException(ErrorCode.DIARY_VERSION_CONFLICT);
        }
        DiaryVO restoredVo = get(spacePublicId, diaryPublicId, userId, stepUpToken);
        searchService.indexDiary(current.getDiaryId());
        syncJournalService.recordDiary(space.getSpaceId(), userId, "UPDATE", restoredVo);
        return restoredVo;
    }

    @Scheduled(cron = "${app.trash.purge-cron:0 20 3 * * *}")
    @Transactional
    public void purgeExpiredTrash() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        for (int batch = 0; batch < 10; batch++) {
            List<Diary> expired = mapper.findExpiredTrash(cutoff, 100);
            if (expired.isEmpty()) break;
            for (Diary diary : expired) {
                List<MediaAsset> media = mediaService.findAssetsByDiary(diary.getDiaryId());
                for (String path : diaryImageMapper.findImagePathsByDiaryId(diary.getDiaryId())) {
                    imageStorageService.deleteAfterCommit(path);
                }
                mapper.hardDelete(diary.getDiaryId());
                mediaService.deleteOrphanedAfterDiaryPurge(media);
            }
        }
    }

    private Diary requireVisibleDiary(DiarySpace space, String diaryPublicId, Integer userId, boolean allowDeleted) {
        Diary diary = mapper.findDiary(space.getSpaceId(), diaryPublicId);
        if (diary == null || (!allowDeleted && diary.getDeletedAt() != null)) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        if ("PRIVATE".equals(diary.getVisibility()) && !diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        return diary;
    }

    private Diary requireForUpdate(DiarySpace space, String diaryPublicId, Integer userId, boolean requireDeleted) {
        Diary diary = mapper.findDiaryForUpdate(space.getSpaceId(), diaryPublicId);
        if (diary == null || (requireDeleted != (diary.getDeletedAt() != null))) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        if ("PRIVATE".equals(diary.getVisibility()) && !diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        return diary;
    }

    private void requireEditPermission(Diary diary, Integer userId) {
        if ("PRIVATE".equals(diary.getVisibility()) && !diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireDeletePermission(Diary diary, SpaceMember member, Integer userId) {
        if (!diary.getUserId().equals(userId) && !"OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有作者或空间所有者可以删除日记");
        }
    }

    private void requireVersion(Diary diary, Integer expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(diary.getVersion())) {
            throw new BusinessException(ErrorCode.DIARY_VERSION_CONFLICT);
        }
    }

    private void requireUnlocked(Diary diary, Integer userId, String stepUpToken) {
        if (Boolean.TRUE.equals(diary.getLocked())) {
            accountSecurityService.requireStepUp(userId, stepUpToken);
        }
    }

    private void applyInput(Diary diary, DiaryWriteDTO dto, String defaultVisibility, boolean defaultLocked) {
        diary.setTitle(dto.getTitle().trim());
        try {
            diary.setDate(Date.valueOf(dto.getDate()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日记日期无效");
        }
        String format = dto.getContentFormat() == null ? "html" : dto.getContentFormat();
        diary.setContentFormat(format);
        diary.setContent("html".equals(format) ? htmlSanitizer.sanitize(dto.getContent()) : dto.getContent());
        diary.setMoodKey(dto.getMoodKey() == null || dto.getMoodKey().isBlank() ? null : dto.getMoodKey().trim());
        diary.setVisibility(dto.getVisibility() == null ? defaultVisibility : dto.getVisibility());
        diary.setLocked(dto.getLocked() == null ? defaultLocked : dto.getLocked());
    }

    private void saveRevision(Diary diary, Integer editorUserId) {
        DiaryRevision revision = new DiaryRevision();
        revision.setDiaryId(diary.getDiaryId());
        revision.setVersion(diary.getVersion());
        revision.setEditorUserId(editorUserId);
        try {
            revision.setSnapshotJson(objectMapper.writeValueAsString(DiarySnapshot.from(diary)));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法保存日记历史版本");
        }
        mapper.insertRevision(revision);
    }

    private DiarySnapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, DiarySnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "历史版本数据损坏");
        }
    }

    private void enrich(Diary diary) {
        diary.setTagList(tagService.findTagsByDiaryId(diary.getDiaryId()));
        diary.setImagePathList(diaryImageService.findImagePathsByDiaryId(diary.getDiaryId()));
        diary.setMediaList(mediaService.findByDiary(diary.getDiaryId()));
    }

    private void enrich(List<Diary> diaries) {
        if (diaries.isEmpty()) return;
        List<Integer> visibleDiaryIds = diaries.stream()
                .filter(diary -> !Boolean.TRUE.equals(diary.getLocked()))
                .map(Diary::getDiaryId).toList();
        if (visibleDiaryIds.isEmpty()) {
            diaries.forEach(diary -> {
                diary.setTagList(Collections.emptyList());
                diary.setImagePathList(Collections.emptyList());
                diary.setMediaList(Collections.emptyList());
            });
            return;
        }
        Map<Integer, List<Tag>> tags = tagService.findTagsByDiaryIds(visibleDiaryIds);
        Map<Integer, List<String>> images = diaryImageMapper.findDiaryImagesByDiaryIds(
                        visibleDiaryIds).stream()
                .collect(Collectors.groupingBy(DiaryImage::getDiaryId, LinkedHashMap::new,
                        Collectors.mapping(DiaryImage::getImagePath, Collectors.toList())));
        Map<Integer, List<MediaAssetVO>> media = mediaService.findByDiaries(visibleDiaryIds);
        diaries.forEach(diary -> {
            if (Boolean.TRUE.equals(diary.getLocked())) {
                diary.setTagList(Collections.emptyList());
                diary.setImagePathList(Collections.emptyList());
                diary.setMediaList(Collections.emptyList());
                return;
            }
            diary.setTagList(tags.getOrDefault(diary.getDiaryId(), Collections.emptyList()));
            diary.setImagePathList(images.getOrDefault(diary.getDiaryId(), Collections.emptyList()));
            diary.setMediaList(media.getOrDefault(diary.getDiaryId(), Collections.emptyList()));
        });
    }

    private String diaryTarget(String spacePublicId, String diaryPublicId) {
        return "/spaces/" + spacePublicId + "/diaries/" + diaryPublicId;
    }

    private String notificationPreview(Diary diary) {
        return Boolean.TRUE.equals(diary.getLocked()) ? "一篇已锁定的日记" : diary.getTitle();
    }
}

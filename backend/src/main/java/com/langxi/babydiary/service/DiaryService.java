package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.CalendarDayVO;
import com.langxi.babydiary.dto.DiaryVO;
import com.langxi.babydiary.dto.TimelineMonthVO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.mapper.DiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DiaryService {

    @Autowired
    private DiaryMapper diaryMapper;

    @Autowired
    private TagService tagService;

    @Autowired
    private HtmlSanitizer htmlSanitizer;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private SpaceService spaceService;

    private boolean hasUploadFiles(MultipartFile[] imageFiles) {
        if (imageFiles == null || imageFiles.length == 0) {
            return false;
        }
        for (MultipartFile file : imageFiles) {
            if (file != null && !file.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public Diary findDiaryById(Integer diaryId) {
        Diary diary = diaryMapper.findDiaryById(diaryId);
        enrichDiary(diary);
        return diary;
    }

    private void prepareDiaryContent(Diary diary) {
        if (diary.getContentFormat() == null || diary.getContentFormat().trim().isEmpty()) {
            diary.setContentFormat("plain");
        }
        if ("html".equals(diary.getContentFormat())) {
            diary.setContent(htmlSanitizer.sanitize(diary.getContent()));
        }
    }

    private void enrichDiary(Diary diary) {
        if (diary != null && diary.getDiaryId() != null) {
            diary.setTagList(tagService.findTagsByDiaryId(diary.getDiaryId()));
            diary.setMediaList(mediaService.findByDiary(diary.getDiaryId()));
        }
    }

    private void enrichDiaries(List<Diary> diaries) {
        if (diaries == null || diaries.isEmpty()) {
            return;
        }
        List<Integer> diaryIds = diaries.stream()
                .map(Diary::getDiaryId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        Map<Integer, List<com.langxi.babydiary.entity.Tag>> tagsByDiaryId = tagService.findTagsByDiaryIds(diaryIds);
        Map<Integer, List<com.langxi.babydiary.dto.MediaAssetVO>> mediaByDiaryId = mediaService.findByDiaries(diaryIds);
        for (Diary diary : diaries) {
            diary.setTagList(tagsByDiaryId.getOrDefault(diary.getDiaryId(), java.util.Collections.emptyList()));
            diary.setMediaList(mediaByDiaryId.getOrDefault(diary.getDiaryId(), java.util.Collections.emptyList()));
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE, CacheNames.DIARY_CALENDAR, CacheNames.PHOTOS}, allEntries = true)
    public void saveDiary(Diary diary, MultipartFile[] imageFiles, List<Integer> tagIds) throws IOException {
        prepareDiaryContent(diary);
        diaryMapper.insertDiary(diary);
        DiarySpace space = spaceService.requirePersonalSpace(diary.getUserId());
        List<String> uploadedAssetIds = uploadImages(space, diary.getUserId(), imageFiles);
        if (!uploadedAssetIds.isEmpty()) {
            mediaService.replaceDiaryMedia(space.getPublicId(), diary.getDiaryId(), diary.getUserId(),
                    List.of(), uploadedAssetIds, null);
        }
        if (tagIds != null) {
            tagService.replaceDiaryTags(diary.getUserId(), diary.getDiaryId(), tagIds);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE, CacheNames.DIARY_CALENDAR, CacheNames.PHOTOS}, allEntries = true)
    public void updateDiary(Diary diary, MultipartFile[] imageFiles, boolean clearImages,
                            List<String> retainedAssetIds, List<Integer> tagIds,
                            List<String> mediaOrder) throws IOException {
        prepareDiaryContent(diary);
        diaryMapper.updateDiary(diary);
        boolean hasNewImages = hasUploadFiles(imageFiles);
        boolean changesMedia = hasNewImages || clearImages || retainedAssetIds != null
                || (mediaOrder != null && !mediaOrder.isEmpty());
        if (changesMedia) {
            DiarySpace space = spaceService.requirePersonalSpace(diary.getUserId());
            List<String> uploadedAssetIds = uploadImages(space, diary.getUserId(), imageFiles);
            mediaService.replaceDiaryMedia(space.getPublicId(), diary.getDiaryId(), diary.getUserId(),
                    clearImages ? List.of() : retainedAssetIds, uploadedAssetIds, mediaOrder);
        }
        if (tagIds != null) {
            tagService.replaceDiaryTags(diary.getUserId(), diary.getDiaryId(), tagIds);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE, CacheNames.DIARY_CALENDAR, CacheNames.PHOTOS}, allEntries = true)
    public void deleteDiary(Integer diaryId) {
        diaryMapper.deleteDiary(diaryId);
    }

    @Cacheable(cacheNames = CacheNames.DIARY_PAGE, key = "'date:' + #userId + ':' + #startDate + ':' + #endDate + ':' + #tagId + ':' + #moodKey + ':' + #page + ':' + #size + ':' + #summary")
    public PageResult<Diary> getDiariesByDateRange(Integer userId, String startDate, String endDate, Integer tagId, String moodKey, int page, int size, boolean summary) {
        int total = diaryMapper.countDiariesByDateRange(userId, startDate, endDate, tagId, moodKey);
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        long offset = Pagination.offset(normalizedPage, normalizedSize);
        List<Diary> diaries = summary
                ? diaryMapper.findDiarySummariesPageByDateRange(userId, startDate, endDate, tagId, moodKey, normalizedSize, offset)
                : diaryMapper.findDiariesPageByDateRange(userId, startDate, endDate, tagId, moodKey, normalizedSize, offset);
        enrichDiaries(diaries);
        return new PageResult<>(diaries, normalizedPage, normalizedSize, (long) total);
    }

    @Cacheable(cacheNames = CacheNames.DIARY_PAGE, key = "'keyword:' + #userId + ':' + #startDate + ':' + #endDate + ':' + #keyword + ':' + #tagId + ':' + #moodKey + ':' + #page + ':' + #size + ':' + #summary")
    public PageResult<Diary> getDiariesByKeyword(Integer userId, String startDate, String endDate,String keyword, Integer tagId, String moodKey, int page, int size, boolean summary) {
        int total = diaryMapper.countDiariesByDateRangeAndKeyword(userId, startDate, endDate, keyword, tagId, moodKey);
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        long offset = Pagination.offset(normalizedPage, normalizedSize);
        List<Diary> diaries = summary
                ? diaryMapper.findDiarySummariesPageByDateRangeAndKeyword(userId, startDate, endDate, keyword, tagId, moodKey, normalizedSize, offset)
                : diaryMapper.findDiariesPageByDateRangeAndKeyword(userId, startDate, endDate, keyword, tagId, moodKey, normalizedSize, offset);
        enrichDiaries(diaries);
        return new PageResult<>(diaries, normalizedPage, normalizedSize, (long) total);
    }

    @Cacheable(cacheNames = CacheNames.DIARY_TIMELINE, key = "#userId + ':' + #year + ':' + #month + ':' + #tagId + ':' + #moodKey")
    public List<TimelineMonthVO> getTimeline(Integer userId, Integer year, Integer month, Integer tagId, String moodKey) {
        DateRange dateRange = timelineDateRange(year, month);
        List<Diary> diaries = diaryMapper.findDiariesForTimeline(userId, dateRange.startDate, dateRange.endDate, tagId, moodKey);
        enrichDiaries(diaries);
        Map<String, List<DiaryVO>> grouped = diaries.stream()
                .collect(Collectors.groupingBy(diary -> diary.getDate().toString().substring(0, 7),
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(DiaryVO::fromEntity, Collectors.toList())));
        return grouped.entrySet().stream().map(entry -> {
            TimelineMonthVO vo = new TimelineMonthVO();
            vo.setMonth(entry.getKey());
            vo.setDiaries(entry.getValue());
            return vo;
        }).collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheNames.DIARY_CALENDAR, key = "#userId + ':' + #year + ':' + #month")
    public List<CalendarDayVO> getCalendar(Integer userId, Integer year, Integer month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return diaryMapper.findCalendarDays(userId, yearMonth.atDay(1).toString(), yearMonth.atEndOfMonth().toString());
    }

    private DateRange timelineDateRange(Integer year, Integer month) {
        if (year == null) {
            return new DateRange(null, null);
        }
        if (month == null) {
            return new DateRange(year + "-01-01", year + "-12-31");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        return new DateRange(yearMonth.atDay(1).toString(), yearMonth.atEndOfMonth().toString());
    }

    private static class DateRange {
        private final String startDate;
        private final String endDate;

        private DateRange(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    private List<String> uploadImages(DiarySpace space, Integer userId, MultipartFile[] files) throws IOException {
        if (!hasUploadFiles(files)) return List.of();
        List<String> uploaded = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                uploaded.add(mediaService.upload(space.getPublicId(), userId, file, null,
                        null, null, null, null, null, null).getAssetId());
            }
        }
        return uploaded;
    }
}

package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.CalendarDayVO;
import com.langxi.babydiary.dto.DiaryVO;
import com.langxi.babydiary.dto.TimelineMonthVO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiaryImage;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DiaryService {

    @Autowired
    private DiaryMapper diaryMapper;

    @Autowired
    private DiaryImageMapper diaryImageMapper;

    @Autowired
    private TagService tagService;

    @Autowired
    private HtmlSanitizer htmlSanitizer;

    @Autowired
    private ImageStorageService imageStorageService;

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
        for (Diary diary : diaries) {
            diary.setTagList(tagsByDiaryId.getOrDefault(diary.getDiaryId(), java.util.Collections.emptyList()));
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE, CacheNames.DIARY_CALENDAR, CacheNames.PHOTOS}, allEntries = true)
    public void saveDiary(Diary diary, MultipartFile[] imageFiles, List<Integer> tagIds) throws IOException {
        prepareDiaryContent(diary);
        diaryMapper.insertDiary(diary);
        Integer diaryId = diary.getDiaryId();

        if (hasUploadFiles(imageFiles)) {
            List<DiaryImage> diaryImages = new ArrayList<>();
            int sort = 1;
            for (MultipartFile imageFile : imageFiles) {
                if (imageFile != null && !imageFile.isEmpty()) {
                    String fileName = imageStorageService.storeImage(imageFile, diaryImagePrefix(diary.getUserId()), true);
                    DiaryImage diaryImage = new DiaryImage();
                    diaryImage.setDiaryId(diaryId);
                    diaryImage.setImagePath(fileName);
                    diaryImage.setSort(sort++);
                    diaryImages.add(diaryImage);
                }
            }
            if (!diaryImages.isEmpty()) {
                diaryImageMapper.insertDiaryImages(diaryImages.toArray(new DiaryImage[0]));
            }
        }
        if (tagIds != null) {
            tagService.replaceDiaryTags(diary.getUserId(), diaryId, tagIds);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE, CacheNames.DIARY_CALENDAR, CacheNames.PHOTOS}, allEntries = true)
    public void updateDiary(Diary diary, MultipartFile[] imageFiles, boolean clearImages, List<String> retainedImagePaths, List<Integer> tagIds, List<String> imageOrder) throws IOException {
        prepareDiaryContent(diary);
        diaryMapper.updateDiary(diary);
        Integer diaryId = diary.getDiaryId();

        boolean hasNewImages = hasUploadFiles(imageFiles);
        boolean hasExplicitRetainedImages = retainedImagePaths != null;
        boolean hasExplicitImageOrder = imageOrder != null && !imageOrder.isEmpty();
        List<DiaryImage> nextImages = new ArrayList<>();

        if (hasNewImages || clearImages || hasExplicitRetainedImages || hasExplicitImageOrder) {
            List<String> existingImagePaths = diaryImageMapper.findImagePathsByDiaryId(diaryId);
            List<String> newImagePaths = new ArrayList<>();
            Set<String> existingSet = new LinkedHashSet<>(existingImagePaths);
            Set<String> retainedSet = new LinkedHashSet<>();
            if (!clearImages && retainedImagePaths != null) {
                for (String retainedImagePath : retainedImagePaths) {
                    if (existingSet.contains(retainedImagePath)) {
                        retainedSet.add(retainedImagePath);
                    }
                }
            } else if (!clearImages && retainedImagePaths == null) {
                retainedSet.addAll(existingImagePaths);
            }

            for (String existingImagePath : existingImagePaths) {
                if (!retainedSet.contains(existingImagePath)) {
                    imageStorageService.deleteAfterCommit(existingImagePath);
                }
            }
            diaryImageMapper.deleteDiaryImageByDiaryId(diaryId);

            if (hasNewImages) {
                for (MultipartFile imageFile : imageFiles) {
                    if (imageFile != null && !imageFile.isEmpty()) {
                        String fileName = imageStorageService.storeImage(imageFile, diaryImagePrefix(diary.getUserId()), true);
                        newImagePaths.add(fileName);
                    }
                }
            }

            List<String> orderedImagePaths = DiaryImageOrderer.order(retainedSet, newImagePaths, imageOrder);
            int sort = 1;
            for (String imagePath : orderedImagePaths) {
                DiaryImage diaryImage = new DiaryImage();
                diaryImage.setDiaryId(diaryId);
                diaryImage.setImagePath(imagePath);
                diaryImage.setSort(sort++);
                nextImages.add(diaryImage);
            }
        }

        if (hasNewImages || clearImages || hasExplicitRetainedImages || hasExplicitImageOrder) {
            if (!nextImages.isEmpty()) {
                diaryImageMapper.insertDiaryImages(nextImages.toArray(new DiaryImage[0]));
            }
        }
        if (tagIds != null) {
            tagService.replaceDiaryTags(diary.getUserId(), diaryId, tagIds);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE, CacheNames.DIARY_CALENDAR, CacheNames.PHOTOS}, allEntries = true)
    public void deleteDiary(Integer diaryId) {
        List<String> imagePathList = diaryImageMapper.findImagePathsByDiaryId(diaryId);
        for (String imagePath : imagePathList) {
            imageStorageService.deleteAfterCommit(imagePath);
        }
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
        setImagePathLists(diaries);
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

    public void setImagePathLists(List<Diary> diaries) {
        if (diaries == null || diaries.isEmpty()) {
            return;
        }
        List<Integer> diaryIds = diaries.stream()
                .map(Diary::getDiaryId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (diaryIds.isEmpty()) {
            return;
        }
        Map<Integer, List<String>> imagesByDiaryId = diaryImageMapper.findDiaryImagesByDiaryIds(diaryIds)
                .stream()
                .collect(Collectors.groupingBy(DiaryImage::getDiaryId,
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(DiaryImage::getImagePath, Collectors.toList())));
        for (Diary diary : diaries) {
            diary.setImagePathList(imagesByDiaryId.getOrDefault(diary.getDiaryId(), java.util.Collections.emptyList()));
        }
    }

    private String diaryImagePrefix(Integer userId) {
        return "diary_" + userId + "_";
    }
}

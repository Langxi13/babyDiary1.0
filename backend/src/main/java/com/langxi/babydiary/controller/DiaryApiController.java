package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.CalendarDayVO;
import com.langxi.babydiary.dto.DiaryVO;
import com.langxi.babydiary.dto.TimelineMonthVO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.DiaryArchiveService;
import com.langxi.babydiary.service.DiaryImageService;
import com.langxi.babydiary.service.DiaryService;
import com.langxi.babydiary.validation.DiaryRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/diaries")
@Tag(name = "日记管理", description = "日记的增删改查、搜索、导出等接口")
public class DiaryApiController {

    @Autowired
    private DiaryService diaryService;

    @Autowired
    private DiaryImageService diaryImageService;

    @Autowired
    private DiaryArchiveService diaryArchiveService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private DiaryRequestValidator requestValidator;

    @Value("${diaryPageSize:5}")
    private int defaultPageSize;

    @GetMapping
    @Operation(summary = "获取日记列表", description = "分页获取日记列表，支持日期范围、关键字、标签和心情筛选")
    public Result<PageResult<DiaryVO>> getDiaryList(
            @Parameter(description = "开始日期") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) String endDate,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "标签ID") @RequestParam(required = false) Integer tagId,
            @Parameter(description = "心情标识") @RequestParam(required = false) String moodKey,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(required = false) Integer size,
            @Parameter(description = "是否只返回列表摘要内容") @RequestParam(required = false, defaultValue = "false") boolean summary) {

        Integer userId = currentUser.getUserId();
        DiaryRequestValidator.DateRange dateRange = requestValidator.diaryListRange(startDate, endDate);
        String normalizedKeyword = requestValidator.normalizeKeyword(keyword);
        String normalizedMoodKey = requestValidator.normalizeMoodKey(moodKey);
        int pageSize = requestValidator.pageSize(size, defaultPageSize);

        PageResult<Diary> diaries;
        if (normalizedKeyword == null) {
            diaries = diaryService.getDiariesByDateRange(userId, dateRange.startDate(), dateRange.endDate(), tagId, normalizedMoodKey, page, pageSize, summary);
        } else {
            diaries = diaryService.getDiariesByKeyword(userId, dateRange.startDate(), dateRange.endDate(), normalizedKeyword, tagId, normalizedMoodKey, page, pageSize, summary);
        }

        diaryService.setImagePathLists(diaries.getContent());
        List<DiaryVO> diaryVOList = diaries.getContent().stream()
                .map(DiaryVO::fromEntity)
                .collect(Collectors.toList());

        PageResult<DiaryVO> pageResult = new PageResult<>(
                diaryVOList,
                diaries.getPageNumber(),
                diaries.getPageSize(),
                diaries.getTotalElements()
        );

        return Result.success(pageResult);
    }

    @GetMapping("/timeline")
    @Operation(summary = "时间轴", description = "按月份聚合日记")
    public Result<List<TimelineMonthVO>> getTimeline(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String moodKey) {
        requestValidator.validateYearMonth(year, month);
        return Result.success(diaryService.getTimeline(
                currentUser.getUserId(), year, month, tagId, requestValidator.normalizeMoodKey(moodKey)));
    }

    @GetMapping("/calendar")
    @Operation(summary = "日历", description = "返回指定月份每天的日记数量")
    public Result<List<CalendarDayVO>> getCalendar(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        requestValidator.validateYearMonth(year, month);
        return Result.success(diaryService.getCalendar(currentUser.getUserId(), year, month));
    }

    @GetMapping("/{diaryId}")
    @Operation(summary = "获取日记详情", description = "根据ID获取日记详细信息")
    public Result<DiaryVO> getDiary(
            @Parameter(description = "日记ID") @PathVariable Integer diaryId) {

        Diary diary = findOwnedDiary(diaryId);
        return Result.success(toDiaryVOWithImages(diary));
    }

    @PostMapping
    @Operation(summary = "创建日记", description = "创建新日记，支持多图片上传")
    public Result<DiaryVO> createDiary(
            @Parameter(description = "日记标题") @RequestParam String title,
            @Parameter(description = "日记内容") @RequestParam String content,
            @Parameter(description = "日记日期") @RequestParam String date,
            @Parameter(description = "内容格式：plain/html") @RequestParam(required = false, defaultValue = "plain") String contentFormat,
            @Parameter(description = "心情标识") @RequestParam(required = false) String moodKey,
            @Parameter(description = "逗号分隔标签ID") @RequestParam(required = false) String tagIds,
            @Parameter(description = "图片文件") @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles) throws IOException {

        requestValidator.validateImageCount(imageFiles);
        DiaryRequestValidator.DiaryInput input = requestValidator.diaryInput(title, content, date, contentFormat, moodKey);
        Integer userId = currentUser.getUserId();

        Diary diary = new Diary();
        diary.setUserId(userId);
        diary.setTitle(input.title());
        diary.setContent(input.content());
        diary.setContentFormat(input.contentFormat());
        diary.setMoodKey(input.moodKey());
        diary.setDate(input.date());

        diaryService.saveDiary(diary, imageFiles, requestValidator.parseTagIds(tagIds));

        Diary savedDiary = diaryService.findDiaryById(diary.getDiaryId());
        savedDiary.setImagePathList(diaryImageService.findImagePathsByDiaryId(savedDiary.getDiaryId()));
        log.info("创建日记成功: userId={}, diaryId={}", userId, diary.getDiaryId());
        return Result.success("创建成功", DiaryVO.fromEntity(savedDiary));
    }

    @PutMapping("/{diaryId}")
    @Operation(summary = "更新日记", description = "更新指定日记，支持更新图片")
    public Result<DiaryVO> updateDiary(
            @Parameter(description = "日记ID") @PathVariable Integer diaryId,
            @Parameter(description = "日记标题") @RequestParam String title,
            @Parameter(description = "日记内容") @RequestParam String content,
            @Parameter(description = "日记日期") @RequestParam String date,
            @Parameter(description = "内容格式：plain/html") @RequestParam(required = false, defaultValue = "plain") String contentFormat,
            @Parameter(description = "心情标识") @RequestParam(required = false) String moodKey,
            @Parameter(description = "逗号分隔标签ID") @RequestParam(required = false) String tagIds,
            @Parameter(description = "图片文件") @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
            @Parameter(description = "保留的旧图片文件名") @RequestParam(value = "retainedImagePaths", required = false) List<String> retainedImagePaths,
            @Parameter(description = "图片排序，existing:文件名 或 new:新图片序号") @RequestParam(value = "imageOrder", required = false) List<String> imageOrder,
            @Parameter(description = "是否清除图片") @RequestParam(value = "clearImages", required = false, defaultValue = "false") boolean clearImages) throws IOException {

        return doUpdateDiary(diaryId, title, content, date, contentFormat, moodKey, tagIds, imageFiles, retainedImagePaths, imageOrder, clearImages);
    }

    @PostMapping("/{diaryId}/update")
    @Operation(summary = "更新日记(POST)", description = "使用POST方式更新日记，支持multipart/form-data")
    public Result<DiaryVO> updateDiaryPost(
            @Parameter(description = "日记ID") @PathVariable Integer diaryId,
            @Parameter(description = "日记标题") @RequestParam String title,
            @Parameter(description = "日记内容") @RequestParam String content,
            @Parameter(description = "日记日期") @RequestParam String date,
            @Parameter(description = "内容格式：plain/html") @RequestParam(required = false, defaultValue = "plain") String contentFormat,
            @Parameter(description = "心情标识") @RequestParam(required = false) String moodKey,
            @Parameter(description = "逗号分隔标签ID") @RequestParam(required = false) String tagIds,
            @Parameter(description = "图片文件") @RequestParam(value = "imageFiles", required = false) MultipartFile[] imageFiles,
            @Parameter(description = "保留的旧图片文件名") @RequestParam(value = "retainedImagePaths", required = false) List<String> retainedImagePaths,
            @Parameter(description = "图片排序，existing:文件名 或 new:新图片序号") @RequestParam(value = "imageOrder", required = false) List<String> imageOrder,
            @Parameter(description = "是否清除图片") @RequestParam(value = "clearImages", required = false, defaultValue = "false") boolean clearImages) throws IOException {

        return doUpdateDiary(diaryId, title, content, date, contentFormat, moodKey, tagIds, imageFiles, retainedImagePaths, imageOrder, clearImages);
    }

    @DeleteMapping("/{diaryId}")
    @Operation(summary = "删除日记", description = "删除指定日记及其关联图片")
    public Result<Void> deleteDiary(
            @Parameter(description = "日记ID") @PathVariable Integer diaryId) {

        Diary diary = findOwnedDiary(diaryId);
        diaryService.deleteDiary(diary.getDiaryId());
        log.info("删除日记成功: userId={}, diaryId={}", currentUser.getUserId(), diaryId);
        return Result.success("删除成功", null);
    }

    @GetMapping("/export")
    @Operation(summary = "导出图片", description = "导出指定日期范围内的图片为ZIP文件")
    public ResponseEntity<FileSystemResource> exportImages(
            @Parameter(description = "开始日期") @RequestParam String startDate,
            @Parameter(description = "结束日期") @RequestParam String endDate) throws IOException {

        DiaryRequestValidator.DateRange dateRange = requestValidator.requiredDateRange(startDate, endDate);
        FileSystemResource zipFile = diaryArchiveService.exportImagesAsZip(
                currentUser.getUserId(), dateRange.startDate(), dateRange.endDate());
        if (zipFile == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=diary_images.zip")
                .body(zipFile);
    }

    private Result<DiaryVO> doUpdateDiary(
            Integer diaryId,
            String title,
            String content,
            String date,
            String contentFormat,
            String moodKey,
            String tagIds,
            MultipartFile[] imageFiles,
            List<String> retainedImagePaths,
            List<String> imageOrder,
            boolean clearImages) throws IOException {

        Diary existingDiary = findOwnedDiary(diaryId);
        Integer userId = existingDiary.getUserId();
        requestValidator.validateImageCount(imageFiles);
        requestValidator.validateImageReferences(retainedImagePaths, imageOrder);
        DiaryRequestValidator.DiaryInput input = requestValidator.diaryInput(title, content, date, contentFormat, moodKey);

        Diary diary = new Diary();
        diary.setDiaryId(diaryId);
        diary.setUserId(userId);
        diary.setTitle(input.title());
        diary.setContent(input.content());
        diary.setContentFormat(input.contentFormat());
        diary.setMoodKey(input.moodKey());
        diary.setDate(input.date());

        diaryService.updateDiary(
                diary, imageFiles, clearImages, retainedImagePaths, requestValidator.parseTagIds(tagIds), imageOrder);

        Diary updatedDiary = diaryService.findDiaryById(diaryId);
        updatedDiary.setImagePathList(diaryImageService.findImagePathsByDiaryId(diaryId));
        log.info("更新日记成功: userId={}, diaryId={}", userId, diaryId);
        return Result.success("更新成功", DiaryVO.fromEntity(updatedDiary));
    }

    private Diary findOwnedDiary(Integer diaryId) {
        Diary diary = diaryService.findDiaryById(diaryId);
        if (diary == null) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        Integer userId = currentUser.getUserId();
        if (!diary.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return diary;
    }

    private DiaryVO toDiaryVOWithImages(Diary diary) {
        diary.setImagePathList(diaryImageService.findImagePathsByDiaryId(diary.getDiaryId()));
        return DiaryVO.fromEntity(diary);
    }

}

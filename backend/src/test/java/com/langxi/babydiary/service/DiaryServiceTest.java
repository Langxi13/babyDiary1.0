package com.langxi.babydiary.service;

import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.Tag;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import com.langxi.babydiary.common.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock
    private DiaryMapper diaryMapper;

    @Mock
    private DiaryImageMapper diaryImageMapper;

    @Mock
    private TagService tagService;

    @Mock
    private HtmlSanitizer htmlSanitizer;

    @Spy
    private ImageStorageService imageStorageService = new ImageStorageService();

    @InjectMocks
    private DiaryService diaryService;

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setUpImageStorage() {
        ReflectionTestUtils.setField(imageStorageService, "uploadDir", uploadDir.toString());
    }

    @Test
    void updateDiaryKeepsRetainedImagesAndDeletesRemovedImages() throws Exception {
        Files.write(uploadDir.resolve("keep.jpg"), new byte[]{1});
        Files.write(uploadDir.resolve("remove.jpg"), new byte[]{2});
        Files.createDirectories(uploadDir.resolve("thumbs/480"));
        Files.write(uploadDir.resolve("thumbs/480/keep.jpg"), new byte[]{1});
        Files.write(uploadDir.resolve("thumbs/480/remove.jpg"), new byte[]{2});

        Diary diary = new Diary();
        diary.setDiaryId(12);
        diary.setUserId(3);
        diary.setTitle("updated");
        diary.setContent("content");
        diary.setDate(Date.valueOf("2026-06-08"));

        when(diaryImageMapper.findImagePathsByDiaryId(12))
                .thenReturn(Arrays.asList("keep.jpg", "remove.jpg"));

        diaryService.updateDiary(diary, null, false, Arrays.asList("keep.jpg"), null, null);

        verify(diaryMapper).updateDiary(diary);
        verify(diaryImageMapper).deleteDiaryImageByDiaryId(12);
        verify(diaryImageMapper).insertDiaryImages(org.mockito.ArgumentMatchers.argThat(images ->
                images.length == 1 && "keep.jpg".equals(images[0].getImagePath()) && images[0].getSort() == 1
        ));
        assertThat(Files.exists(uploadDir.resolve("keep.jpg"))).isTrue();
        assertThat(Files.exists(uploadDir.resolve("remove.jpg"))).isFalse();
        assertThat(Files.exists(uploadDir.resolve("thumbs/480/keep.jpg"))).isTrue();
        assertThat(Files.exists(uploadDir.resolve("thumbs/480/remove.jpg"))).isFalse();
    }

    @Test
    void updateDiaryUsesSubmittedImageOrderForExistingAndNewImages() throws Exception {
        Files.write(uploadDir.resolve("keep.jpg"), new byte[]{1});
        Files.createDirectories(uploadDir.resolve("thumbs/480"));
        Files.write(uploadDir.resolve("thumbs/480/keep.jpg"), new byte[]{1});

        Diary diary = new Diary();
        diary.setDiaryId(12);
        diary.setUserId(3);
        diary.setTitle("updated");
        diary.setContent("content");
        diary.setDate(Date.valueOf("2026-06-08"));

        MockMultipartFile newImage = new MockMultipartFile(
                "imageFiles",
                "new.jpg",
                "image/jpeg",
                imageBytes(1200, 900, "jpg"));

        when(diaryImageMapper.findImagePathsByDiaryId(12)).thenReturn(Collections.singletonList("keep.jpg"));
        diaryService.updateDiary(
                diary,
                new MockMultipartFile[]{newImage},
                false,
                Collections.singletonList("keep.jpg"),
                Collections.emptyList(),
                Arrays.asList("new:0", "existing:keep.jpg"));

        verify(diaryImageMapper).insertDiaryImages(argThat(images ->
                images.length == 2
                        && images[0].getSort() == 1
                        && images[0].getImagePath().startsWith("diary_3_")
                        && images[0].getImagePath().endsWith(".jpg")
                        && images[1].getSort() == 2
                        && "keep.jpg".equals(images[1].getImagePath())
        ));
    }

    @Test
    void addingImagesWithoutRetentionParametersKeepsExistingImages() throws Exception {
        Files.write(uploadDir.resolve("keep.jpg"), new byte[]{1});
        Diary diary = new Diary();
        diary.setDiaryId(12);
        diary.setUserId(3);
        diary.setTitle("updated");
        diary.setContent("content");
        diary.setDate(Date.valueOf("2026-06-08"));
        MockMultipartFile newImage = new MockMultipartFile(
                "imageFiles", "new.jpg", "image/jpeg", imageBytes(800, 600, "jpg"));

        when(diaryImageMapper.findImagePathsByDiaryId(12)).thenReturn(Collections.singletonList("keep.jpg"));

        diaryService.updateDiary(diary, new MockMultipartFile[]{newImage}, false, null, null, null);

        verify(diaryImageMapper).insertDiaryImages(argThat(images ->
                images.length == 2
                        && "keep.jpg".equals(images[0].getImagePath())
                        && images[1].getImagePath().startsWith("diary_3_")));
        assertThat(Files.exists(uploadDir.resolve("keep.jpg"))).isTrue();
    }

    @Test
    void saveDiaryCreatesThumbnailForUploadedImages() throws Exception {
        Diary diary = new Diary();
        diary.setUserId(3);
        diary.setTitle("new");
        diary.setContent("content");
        diary.setDate(Date.valueOf("2026-07-06"));

        doAnswer(invocation -> {
            Diary argument = invocation.getArgument(0);
            argument.setDiaryId(30);
            return null;
        }).when(diaryMapper).insertDiary(diary);
        MockMultipartFile image = new MockMultipartFile(
                "imageFiles",
                "photo.jpg",
                "image/jpeg",
                imageBytes(1200, 900, "jpg"));

        diaryService.saveDiary(diary, new MockMultipartFile[]{image}, null);

        Path savedImage = Files.list(uploadDir)
                .filter(path -> path.getFileName().toString().startsWith("diary_3_"))
                .filter(path -> path.getFileName().toString().endsWith(".jpg"))
                .findFirst()
                .orElseThrow();
        Path thumbnail = uploadDir.resolve("thumbs/480").resolve(savedImage.getFileName().toString());

        assertThat(Files.exists(savedImage)).isTrue();
        assertThat(Files.exists(thumbnail)).isTrue();
    }

    @Test
    void deleteDiaryMovesEntryToTrashWithoutDeletingImages() {
        assertThatCode(() -> diaryService.deleteDiary(12)).doesNotThrowAnyException();

        verify(diaryImageMapper, never()).findImagePathsByDiaryId(12);
        verify(diaryImageMapper, never()).deleteDiaryImageByDiaryId(12);
        verify(diaryMapper).deleteDiary(12);
    }

    @Test
    void summaryDateRangeUsesSummaryQueryAndKeepsOnlyPreviewContent() {
        Diary diary = new Diary();
        diary.setDiaryId(21);
        diary.setUserId(3);
        diary.setTitle("summary");
        diary.setDate(Date.valueOf("2026-07-03"));
        diary.setContent("preview");

        when(diaryMapper.countDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null))
                .thenReturn(1);
        when(diaryMapper.findDiarySummariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L))
                .thenReturn(Collections.singletonList(diary));
        when(tagService.findTagsByDiaryIds(Collections.singletonList(21))).thenReturn(Collections.emptyMap());

        PageResult<Diary> result = diaryService.getDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null, 0, 5, true);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("preview");
        verify(diaryMapper).findDiarySummariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L);
        verify(diaryMapper, never()).findDiariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L);
    }

    @Test
    void dateRangeCapsPageSizeToProtectDatabaseQueries() {
        when(diaryMapper.countDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null))
                .thenReturn(0);
        when(diaryMapper.findDiariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 100, 0L))
                .thenReturn(Collections.emptyList());

        PageResult<Diary> result = diaryService.getDiariesByDateRange(
                3, "2026-07-01", "2026-07-31", null, null, -1, 10_000, false);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    void dateRangeEnrichesTagsInBatch() {
        Diary first = new Diary();
        first.setDiaryId(21);
        first.setUserId(3);
        first.setTitle("first");
        first.setDate(Date.valueOf("2026-07-03"));
        Diary second = new Diary();
        second.setDiaryId(22);
        second.setUserId(3);
        second.setTitle("second");
        second.setDate(Date.valueOf("2026-07-02"));
        List<Diary> diaries = Arrays.asList(first, second);

        Tag tag = new Tag();
        tag.setTagId(7);
        tag.setUserId(3);
        tag.setName("日常");

        Map<Integer, List<Tag>> tagsByDiaryId = new HashMap<>();
        tagsByDiaryId.put(21, Collections.singletonList(tag));

        when(diaryMapper.countDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null))
                .thenReturn(2);
        when(diaryMapper.findDiariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L))
                .thenReturn(diaries);
        when(tagService.findTagsByDiaryIds(Arrays.asList(21, 22))).thenReturn(tagsByDiaryId);

        PageResult<Diary> result = diaryService.getDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null, 0, 5, false);

        assertThat(result.getContent().get(0).getTagList()).containsExactly(tag);
        assertThat(result.getContent().get(1).getTagList()).isEmpty();
        verify(tagService).findTagsByDiaryIds(Arrays.asList(21, 22));
        verify(tagService, never()).findTagsByDiaryId(anyInt());
    }

    @Test
    void timelineUsesDateRangeWhenYearAndMonthAreProvided() {
        Diary diary = new Diary();
        diary.setDiaryId(21);
        diary.setUserId(3);
        diary.setTitle("first");
        diary.setDate(Date.valueOf("2026-07-03"));

        when(diaryMapper.findDiariesForTimeline(3, "2026-07-01", "2026-07-31", null, null))
                .thenReturn(Collections.singletonList(diary));
        when(tagService.findTagsByDiaryIds(Collections.singletonList(21))).thenReturn(Collections.emptyMap());
        when(diaryImageMapper.findDiaryImagesByDiaryIds(Collections.singletonList(21))).thenReturn(Collections.emptyList());

        List<com.langxi.babydiary.dto.TimelineMonthVO> timeline = diaryService.getTimeline(3, 2026, 7, null, null);

        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).getMonth()).isEqualTo("2026-07");
        verify(diaryMapper).findDiariesForTimeline(3, "2026-07-01", "2026-07-31", null, null);
    }

    @Test
    void calendarUsesDateRangeForTheMonth() {
        diaryService.getCalendar(3, 2026, 7);

        verify(diaryMapper).findCalendarDays(3, "2026-07-01", "2026-07-31");
    }

    private byte[] imageBytes(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}

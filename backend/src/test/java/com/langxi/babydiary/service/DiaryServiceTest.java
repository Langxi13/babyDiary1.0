package com.langxi.babydiary.service;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.dto.MediaAssetVO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.Tag;
import com.langxi.babydiary.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {
    @Mock private DiaryMapper diaryMapper;
    @Mock private TagService tagService;
    @Mock private HtmlSanitizer htmlSanitizer;
    @Mock private MediaService mediaService;
    @Mock private SpaceService spaceService;
    @InjectMocks private DiaryService diaryService;

    @Test
    void updateDiaryReplacesLinksUsingRetainedAssetIdsWithoutDeletingAssets() throws Exception {
        Diary diary = diary(12);
        DiarySpace space = personalSpace();
        when(spaceService.requirePersonalSpace(3)).thenReturn(space);

        diaryService.updateDiary(diary, null, false,
                List.of("11111111-1111-1111-1111-111111111111"), null,
                List.of("existing:11111111-1111-1111-1111-111111111111"));

        verify(diaryMapper).updateDiary(diary);
        verify(mediaService).replaceDiaryMedia(space.getPublicId(), 12, 3,
                List.of("11111111-1111-1111-1111-111111111111"), List.of(),
                List.of("existing:11111111-1111-1111-1111-111111111111"));
        verify(mediaService, never()).delete(any(), any(), anyInt(), any());
    }

    @Test
    void replacingAllMediaDetachesOldLinksOnFirstUpdate() throws Exception {
        Diary diary = diary(12);
        DiarySpace space = personalSpace();
        MockMultipartFile replacement = new MockMultipartFile("imageFiles", "new.png", "image/png", new byte[]{1});
        MediaAssetVO uploaded = new MediaAssetVO();
        uploaded.setAssetId("22222222-2222-2222-2222-222222222222");
        when(spaceService.requirePersonalSpace(3)).thenReturn(space);
        when(mediaService.upload(space.getPublicId(), 3, replacement, null,
                null, null, null, null, null, null)).thenReturn(uploaded);

        diaryService.updateDiary(diary, new MockMultipartFile[]{replacement}, true,
                null, Collections.emptyList(), List.of("new:0"));

        verify(mediaService).replaceDiaryMedia(space.getPublicId(), 12, 3, List.of(),
                List.of(uploaded.getAssetId()), List.of("new:0"));
    }

    @Test
    void updateWithoutMediaParametersLeavesLinksUntouched() throws Exception {
        Diary diary = diary(12);
        diaryService.updateDiary(diary, null, false, null, null, null);
        verify(diaryMapper).updateDiary(diary);
        verify(mediaService, never()).replaceDiaryMedia(any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void summaryDateRangeUsesSummaryQueryAndBatchEnrichment() {
        Diary diary = diary(21);
        diary.setContent("preview");
        when(diaryMapper.countDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null)).thenReturn(1);
        when(diaryMapper.findDiarySummariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L))
                .thenReturn(List.of(diary));
        when(tagService.findTagsByDiaryIds(List.of(21))).thenReturn(Collections.emptyMap());
        when(mediaService.findByDiaries(List.of(21))).thenReturn(Collections.emptyMap());

        PageResult<Diary> result = diaryService.getDiariesByDateRange(
                3, "2026-07-01", "2026-07-31", null, null, 0, 5, true);

        assertThat(result.getContent()).singleElement().extracting(Diary::getContent).isEqualTo("preview");
        verify(diaryMapper, never()).findDiariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L);
    }

    @Test
    void dateRangeEnrichesTagsAndMediaInBatches() {
        Diary first = diary(21);
        Diary second = diary(22);
        Tag tag = new Tag();
        tag.setTagId(7);
        Map<Integer, List<Tag>> tags = new HashMap<>();
        tags.put(21, List.of(tag));
        MediaAssetVO media = new MediaAssetVO();
        media.setAssetId("33333333-3333-3333-3333-333333333333");
        when(diaryMapper.countDiariesByDateRange(3, "2026-07-01", "2026-07-31", null, null)).thenReturn(2);
        when(diaryMapper.findDiariesPageByDateRange(3, "2026-07-01", "2026-07-31", null, null, 5, 0L))
                .thenReturn(Arrays.asList(first, second));
        when(tagService.findTagsByDiaryIds(Arrays.asList(21, 22))).thenReturn(tags);
        when(mediaService.findByDiaries(Arrays.asList(21, 22))).thenReturn(Map.of(21, List.of(media), 22, List.of()));

        PageResult<Diary> result = diaryService.getDiariesByDateRange(
                3, "2026-07-01", "2026-07-31", null, null, 0, 5, false);

        assertThat(result.getContent().get(0).getTagList()).containsExactly(tag);
        assertThat(result.getContent().get(0).getMediaList()).containsExactly(media);
        verify(tagService, never()).findTagsByDiaryId(anyInt());
    }

    @Test
    void timelineUsesRequestedMonthAndUnifiedMedia() {
        Diary diary = diary(21);
        when(diaryMapper.findDiariesForTimeline(3, "2026-07-01", "2026-07-31", null, null)).thenReturn(List.of(diary));
        when(tagService.findTagsByDiaryIds(List.of(21))).thenReturn(Collections.emptyMap());
        when(mediaService.findByDiaries(List.of(21))).thenReturn(Collections.emptyMap());

        var timeline = diaryService.getTimeline(3, 2026, 7, null, null);

        assertThat(timeline).singleElement().extracting(value -> value.getMonth()).isEqualTo("2026-07");
    }

    private Diary diary(int id) {
        Diary diary = new Diary();
        diary.setDiaryId(id);
        diary.setUserId(3);
        diary.setTitle("title");
        diary.setContent("content");
        diary.setContentFormat("plain");
        diary.setDate(Date.valueOf("2026-07-03"));
        return diary;
    }

    private DiarySpace personalSpace() {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        space.setPublicId("space-one");
        return space;
    }
}

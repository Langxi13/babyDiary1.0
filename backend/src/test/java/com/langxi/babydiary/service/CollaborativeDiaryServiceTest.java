package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.dto.DiaryVO;
import com.langxi.babydiary.dto.DiaryWriteDTO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.SpaceMember;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborativeDiaryServiceTest {
    @Mock private CollaborationMapper mapper;
    @Mock private SpaceService spaceService;
    @Mock private TagService tagService;
    @Mock private DiaryImageService diaryImageService;
    @Mock private DiaryImageMapper diaryImageMapper;
    @Mock private HtmlSanitizer htmlSanitizer;
    @Mock private AccountSecurityService accountSecurityService;
    @Mock private ImageStorageService imageStorageService;
    @Mock private NotificationService notificationService;
    @Mock private SyncJournalService syncJournalService;
    @Mock private SearchService searchService;
    @Mock private MediaService mediaService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private CollaborativeDiaryService service;

    @Test
    void lockedDiaryListDoesNotExposeMoodTagsOrMedia() {
        DiarySpace space = space();
        Diary locked = diary(10, "diary-one", 1, true, 2);
        locked.setMoodKey("happy");
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.countDiaries(7L, 2, null, null, null, false)).thenReturn(1);
        when(mapper.findDiaryPage(7L, 2, null, null, null, false, 10, 0L))
                .thenReturn(Collections.singletonList(locked));

        PageResult<DiaryVO> result = service.list("space-one", 2, null, null, null, false, 0, 10);

        DiaryVO value = result.getContent().get(0);
        assertThat(value.getTitle()).isEqualTo("已锁定的日记");
        assertThat(value.getContent()).isEmpty();
        assertThat(value.getMoodKey()).isNull();
        assertThat(value.getTags()).isEmpty();
        assertThat(value.getImagePathList()).isEmpty();
        assertThat(value.getMedia()).isEmpty();
        verifyNoInteractions(tagService, diaryImageMapper, mediaService);
    }

    @Test
    void updatePreservesExistingLockWhenFieldIsOmitted() {
        DiarySpace space = space();
        Diary current = diary(10, "diary-one", 1, true, 2);
        Diary updated = diary(10, "diary-one", 1, true, 3);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findDiaryForUpdate(7L, "diary-one")).thenReturn(current);
        when(mapper.updateDiary(any(Diary.class), eq(2))).thenReturn(1);
        when(mapper.findDiary(7L, "diary-one")).thenReturn(updated);
        when(tagService.findTagsByDiaryId(10)).thenReturn(Collections.emptyList());
        when(diaryImageService.findImagePathsByDiaryId(10)).thenReturn(Collections.emptyList());
        when(mediaService.findByDiary(10)).thenReturn(Collections.emptyList());

        DiaryWriteDTO dto = writeDto();
        dto.setLocked(null);
        service.update("space-one", "diary-one", 1, dto, 2, "step-up");

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(mapper).updateDiary(captor.capture(), eq(2));
        assertThat(captor.getValue().getLocked()).isTrue();
        verify(notificationService).notifySpaceMembers(
                7L, 1, "DIARY_UPDATED", "共同日记已更新", "一篇已锁定的日记",
                "/spaces/space-one/diaries/diary-one", "diary-one:3");
    }

    @Test
    void collaboratorCannotChangeAnotherAuthorsLock() {
        DiarySpace space = space();
        Diary current = diary(10, "diary-one", 1, false, 2);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(spaceService.requireMember(space, 2)).thenReturn(member(2, "MEMBER"));
        when(mapper.findDiaryForUpdate(7L, "diary-one")).thenReturn(current);
        DiaryWriteDTO dto = writeDto();
        dto.setLocked(true);

        assertThatThrownBy(() -> service.update("space-one", "diary-one", 2, dto, 2, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(mapper, never()).updateDiary(any(), any());
    }

    private DiarySpace space() {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        space.setPublicId("space-one");
        space.setType("SHARED");
        return space;
    }

    private SpaceMember member(int userId, String role) {
        SpaceMember member = new SpaceMember();
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private Diary diary(int id, String publicId, int authorId, boolean locked, int version) {
        Diary diary = new Diary();
        diary.setDiaryId(id);
        diary.setPublicId(publicId);
        diary.setUserId(authorId);
        diary.setSpaceId(7L);
        diary.setTitle("私密标题");
        diary.setDate(Date.valueOf("2026-07-11"));
        diary.setContent("私密正文");
        diary.setContentFormat("plain");
        diary.setVisibility("SHARED");
        diary.setLocked(locked);
        diary.setVersion(version);
        return diary;
    }

    private DiaryWriteDTO writeDto() {
        DiaryWriteDTO dto = new DiaryWriteDTO();
        dto.setTitle("更新标题");
        dto.setDate("2026-07-11");
        dto.setContent("更新正文");
        dto.setContentFormat("plain");
        dto.setVisibility("SHARED");
        return dto;
    }
}

package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryInteractionServiceTest {
    @Mock private CollaborationMapper mapper;
    @Mock private SpaceService spaceService;
    @Mock private AccountSecurityService accountSecurityService;
    @Mock private NotificationService notificationService;

    @InjectMocks private DiaryInteractionService service;

    @Test
    void lockedDiaryInteractionsRequireStepUp() {
        DiarySpace space = space();
        Diary diary = diary("SHARED", 1, true);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findDiary(7L, "diary-one")).thenReturn(diary);
        when(mapper.findComments(9)).thenReturn(Collections.emptyList());

        service.comments("space-one", "diary-one", 2, "step-up");

        verify(accountSecurityService).requireStepUp(2, "step-up");
    }

    @Test
    void privateDiaryInteractionsAreHiddenFromOtherMembers() {
        DiarySpace space = space();
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findDiary(7L, "diary-one")).thenReturn(diary("PRIVATE", 1, false));

        assertThatThrownBy(() -> service.comments("space-one", "diary-one", 2, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.DIARY_NOT_FOUND.getCode()));
    }

    private DiarySpace space() {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        return space;
    }

    private Diary diary(String visibility, int ownerId, boolean locked) {
        Diary diary = new Diary();
        diary.setDiaryId(9);
        diary.setUserId(ownerId);
        diary.setVisibility(visibility);
        diary.setLocked(locked);
        return diary;
    }
}

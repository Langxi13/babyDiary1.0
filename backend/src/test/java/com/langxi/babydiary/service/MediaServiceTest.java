package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.MediaAsset;
import com.langxi.babydiary.entity.SpaceMember;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.MediaMapper;
import com.langxi.babydiary.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {
    @Mock private MediaMapper mapper;
    @Mock private SpaceService spaceService;
    @Mock private CollaborationMapper diaryMapper;
    @Mock private ObjectStorage storage;
    @Mock private MediaAccessTokenService tokenService;
    @Mock private AccountSecurityService accountSecurityService;

    @InjectMocks private MediaService service;

    @Test
    void uploadRepairsStorageUsageBeforeCheckingQuota() throws Exception {
        DiarySpace space = space();
        MockMultipartFile file = pngFile();

        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findUsedBytesForUpdate(7L)).thenReturn(0L);
        when(mapper.findQuotaBytes(7L)).thenReturn(1024L);
        when(storage.provider()).thenReturn("local");
        when(tokenService.sign(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("original")))
                .thenReturn(new MediaAccessTokenService.SignedMediaUrl("/signed", 1L));

        service.upload("space-one", 1, file, null, null, null, null, null, null, null);

        verify(spaceService).ensureStorageUsage(7L);
        verify(mapper).findUsedBytesForUpdate(7L);
    }

    @Test
    void lockedDiaryMediaUploadRequiresStepUpBeforeWritingObject() {
        DiarySpace space = space();
        Diary diary = new Diary();
        diary.setDiaryId(12);
        diary.setUserId(1);
        diary.setVisibility("PRIVATE");
        diary.setLocked(true);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(diaryMapper.findDiary(7L, "locked-diary")).thenReturn(diary);
        doThrow(new BusinessException(ErrorCode.DIARY_LOCKED))
                .when(accountSecurityService).requireStepUp(1, null);

        assertThatThrownBy(() -> service.upload("space-one", 1, pngFile(), "locked-diary",
                null, null, null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.DIARY_LOCKED.getCode()));

        verify(accountSecurityService).requireStepUp(1, null);
        verifyNoInteractions(storage);
    }

    @Test
    void spaceMembershipAloneDoesNotExposePrivateMedia() {
        DiarySpace space = space();
        MediaAsset asset = asset(1);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findByPublicId("asset-one")).thenReturn(asset);
        when(mapper.countAccessibleSharedLinks(9L)).thenReturn(0);

        assertThatThrownBy(() -> service.requireAccessible("space-one", "asset-one", 2, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND.getCode()));
    }

    @Test
    void sharedUnlockedDiaryAllowsMemberMediaAccess() {
        DiarySpace space = space();
        MediaAsset asset = asset(1);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findByPublicId("asset-one")).thenReturn(asset);
        when(mapper.countAccessibleSharedLinks(9L)).thenReturn(1);

        assertThat(service.requireAccessible("space-one", "asset-one", 2, null)).isSameAs(asset);
    }

    @Test
    void ownerMustStepUpBeforeOpeningMediaLinkedToLockedDiary() {
        DiarySpace space = space();
        MediaAsset asset = asset(2);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(mapper.findByPublicId("asset-one")).thenReturn(asset);
        when(mapper.countLockedLinks(9L)).thenReturn(1);

        assertThat(service.requireAccessible("space-one", "asset-one", 2, "step-up")).isSameAs(asset);
        verify(accountSecurityService).requireStepUp(2, "step-up");
    }

    private DiarySpace space() {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        space.setPublicId("space-one");
        return space;
    }

    private MediaAsset asset(int ownerId) {
        MediaAsset asset = new MediaAsset();
        asset.setAssetId(9L);
        asset.setPublicId("asset-one");
        asset.setSpaceId(7L);
        asset.setOwnerUserId(ownerId);
        return asset;
    }

    private MockMultipartFile pngFile() {
        byte[] png = new byte[32];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47};
        System.arraycopy(signature, 0, png, 0, signature.length);
        Arrays.fill(png, signature.length, png.length, (byte) 1);
        return new MockMultipartFile("file", "test.png", "image/png", png);
    }
}

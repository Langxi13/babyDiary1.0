package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.MediaAssetVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AnniversaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnniversaryServiceTest {
    @Mock private AnniversaryMapper anniversaryMapper;
    @Mock private MediaService mediaService;
    @Mock private SpaceService spaceService;
    @InjectMocks private AnniversaryService service;

    @Test
    void uploadCoverReturnsUnifiedAssetId() throws Exception {
        DiarySpace space = new DiarySpace();
        space.setPublicId("space-one");
        MediaAssetVO uploaded = new MediaAssetVO();
        uploaded.setAssetId("11111111-1111-1111-1111-111111111111");
        MockMultipartFile cover = new MockMultipartFile("coverFile", "cover.jpg", "image/jpeg", new byte[]{1});
        when(spaceService.requirePersonalSpace(3)).thenReturn(space);
        when(mediaService.upload("space-one", 3, cover, null, "纪念日封面",
                null, null, null, null, null)).thenReturn(uploaded);

        assertThat(service.uploadCover(3, cover)).isEqualTo(uploaded.getAssetId());
    }

    @Test
    void uploadCoverRejectsEmptyFileBeforeStorage() {
        MockMultipartFile empty = new MockMultipartFile("coverFile", new byte[0]);
        assertThatThrownBy(() -> service.uploadCover(3, empty))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_UPLOAD_FAILED.getCode()));
    }
}

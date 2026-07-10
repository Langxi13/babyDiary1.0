package com.langxi.babydiary.service;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private PhotoMapper photoMapper;

    @InjectMocks
    private PhotoService photoService;

    @Test
    void photoPageNormalizesBoundsAndQueriesOnlyRequestedSlice() {
        Photo photo = new Photo();
        photo.setImageId(88);
        when(photoMapper.countPhotos(3, null, null, null, null, true)).thenReturn(125);
        when(photoMapper.findPhotoPage(3, null, null, null, null, true, 100, 0))
                .thenReturn(Collections.singletonList(photo));

        PageResult<Photo> result = photoService.findPhotoPage(3, null, null, null, null, true, -1, 1000);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(100);
        assertThat(result.getTotalElements()).isEqualTo(125);
        assertThat(result.getContent()).containsExactly(photo);
        verify(photoMapper).findPhotoPage(3, null, null, null, null, true, 100, 0);
    }
}

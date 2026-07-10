package com.langxi.babydiary.service;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.dto.AlbumGroupVO;
import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.AlbumGroup;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AlbumMapper;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock
    private AlbumMapper albumMapper;

    @Mock
    private PhotoMapper photoMapper;

    @Mock
    private PhotoService photoService;

    @InjectMocks
    private AlbumService albumService;

    @Test
    void listGroupsBuildsSystemFavoriteYearAlbumsAndFixedAiGroup() {
        AlbumGroup aiGroup = new AlbumGroup();
        aiGroup.setGroupId(8);
        aiGroup.setUserId(3);
        aiGroup.setType("AI");
        aiGroup.setName("AI 整理");

        when(photoMapper.findPhotoYears(3)).thenReturn(Arrays.asList(2026, 2025));
        when(photoMapper.countPhotos(3, null, null, null, null, null)).thenReturn(12);
        when(photoMapper.countPhotos(3, null, null, null, null, true)).thenReturn(3);
        when(photoMapper.countPhotos(3, "2026-01-01", "2026-12-31", null, null, null)).thenReturn(8);
        when(photoMapper.countPhotos(3, "2025-01-01", "2025-12-31", null, null, null)).thenReturn(4);
        when(photoMapper.findCoverImagePath(3, null, null, null)).thenReturn("all-cover.jpg");
        when(photoMapper.findCoverImagePath(3, null, null, true)).thenReturn("favorite-cover.jpg");
        when(photoMapper.findCoverImagePath(3, "2026-01-01", "2026-12-31", null)).thenReturn("year-2026.jpg");
        when(photoMapper.findCoverImagePath(3, "2025-01-01", "2025-12-31", null)).thenReturn("year-2025.jpg");
        when(albumMapper.ensureAiGroup(3)).thenReturn(aiGroup);
        when(albumMapper.findGroupsByUserId(3)).thenReturn(Collections.singletonList(aiGroup));
        when(albumMapper.findAlbumsByGroupIds(Collections.singletonList(8))).thenReturn(Collections.emptyList());

        List<AlbumGroupVO> groups = albumService.listGroups(3);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getType()).isEqualTo("SYSTEM");
        assertThat(groups.get(0).getEditable()).isFalse();
        assertThat(groups.get(0).getAlbums()).extracting("systemKey")
                .containsExactly("all", "favorites", "year:2026", "year:2025");
        assertThat(groups.get(0).getAlbums()).extracting("coverImagePath")
                .containsExactly("all-cover.jpg", "favorite-cover.jpg", "year-2026.jpg", "year-2025.jpg");
        assertThat(groups.get(0).getAlbums()).extracting("editable")
                .containsExactly(false, false, false, false);
        assertThat(groups.get(1).getType()).isEqualTo("AI");
        assertThat(groups.get(1).getEditable()).isFalse();
    }

    @Test
    void favoriteSystemAlbumReturnsOnlyFavoritePhotos() {
        albumService.findSystemPhotos(3, "favorites");

        verify(photoMapper).findPhotos(3, null, null, null, null, true);
    }

    @Test
    void systemAlbumPageUsesCountAndBoundedPhotoQuery() {
        Photo photo = ownedPhoto(50, 3);
        PageResult<Photo> page = new PageResult<>(Collections.singletonList(photo), 1, 12, 30L);
        when(photoService.findPhotoPage(3, "2026-01-01", "2026-12-31", null, null, null, 1, 12))
                .thenReturn(page);

        PageResult<Photo> result = albumService.findSystemPhotoPage(3, "year:2026", 1, 12);

        assertThat(result.getContent()).containsExactly(photo);
        assertThat(result.getTotalElements()).isEqualTo(30);
        assertThat(result.getPageNumber()).isEqualTo(1);
        verify(photoService).findPhotoPage(3, "2026-01-01", "2026-12-31", null, null, null, 1, 12);
    }

    @Test
    void customAlbumPageChecksOwnershipBeforeReadingPhotos() {
        Photo photo = ownedPhoto(50, 3);
        when(albumMapper.findAlbumById(3, 11)).thenReturn(editableAlbum(11, 3));
        when(albumMapper.countAlbumPhotos(3, 11)).thenReturn(25);
        when(albumMapper.findAlbumPhotoPage(3, 11, 20, 20)).thenReturn(Collections.singletonList(photo));

        PageResult<Photo> result = albumService.findAlbumPhotoPage(3, 11, 1, 20);

        assertThat(result.getContent()).containsExactly(photo);
        assertThat(result.getTotalElements()).isEqualTo(25);
        verify(albumMapper).findAlbumPhotoPage(3, 11, 20, 20);
    }

    @Test
    void samePhotoCanBeIndexedIntoMultipleEditableAlbums() {
        Album first = editableAlbum(11, 3);
        Album second = editableAlbum(12, 3);
        when(albumMapper.findAlbumById(3, 11)).thenReturn(first);
        when(albumMapper.findAlbumById(3, 12)).thenReturn(second);
        Photo ownedPhoto = new Photo();
        ownedPhoto.setImageId(50);
        ownedPhoto.setUserId(3);
        when(photoMapper.findPhotosByIds(3, Collections.singletonList(50))).thenReturn(Collections.singletonList(ownedPhoto));

        albumService.addPhotos(3, 11, Collections.singletonList(50));
        albumService.addPhotos(3, 12, Collections.singletonList(50));

        verify(albumMapper).insertAlbumPhotos(11, Collections.singletonList(50));
        verify(albumMapper).insertAlbumPhotos(12, Collections.singletonList(50));
    }

    @Test
    void addPhotosRejectsImageIdsNotOwnedByCurrentUser() {
        when(albumMapper.findAlbumById(3, 11)).thenReturn(editableAlbum(11, 3));
        when(photoMapper.findPhotosByIds(3, Arrays.asList(50, 99)))
                .thenReturn(Collections.singletonList(ownedPhoto(50, 3)));

        assertThatThrownBy(() -> albumService.addPhotos(3, 11, Arrays.asList(50, 99)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("照片");
    }

    @Test
    void systemAlbumsCannotBeEdited() {
        Album system = editableAlbum(99, 3);
        system.setType("SYSTEM");
        when(albumMapper.findAlbumById(3, 99)).thenReturn(system);

        assertThatThrownBy(() -> albumService.addPhotos(3, 99, Collections.singletonList(50)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统相册不可编辑");
    }

    private Album editableAlbum(Integer albumId, Integer userId) {
        Album album = new Album();
        album.setAlbumId(albumId);
        album.setUserId(userId);
        album.setGroupId(8);
        album.setType("AI");
        album.setName("旅行");
        return album;
    }

    private Photo ownedPhoto(Integer imageId, Integer userId) {
        Photo photo = new Photo();
        photo.setImageId(imageId);
        photo.setUserId(userId);
        return photo;
    }
}

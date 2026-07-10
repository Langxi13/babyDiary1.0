package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.AiAlbumProposalRequestDTO;
import com.langxi.babydiary.entity.AiAlbumProposal;
import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiaryImage;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiAlbumProposalMapper;
import com.langxi.babydiary.mapper.AlbumMapper;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAlbumProposalServiceTest {

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private OpenAiCompatibleClient aiClient;

    @Mock
    private DiaryMapper diaryMapper;

    @Mock
    private DiaryImageMapper diaryImageMapper;

    @Mock
    private AlbumMapper albumMapper;

    @Mock
    private PhotoMapper photoMapper;

    @Mock
    private AiAlbumProposalMapper proposalMapper;

    @InjectMocks
    private AiAlbumProposalService proposalService;

    @Test
    void generateProposalStoresPendingJsonWithoutCreatingAlbums() {
        Diary travel = diary(21, "2026-05-02", "巴黎第一天", "到了卢浮宫和塞纳河。");
        Diary note = diary(22, "2026-05-03", "随手记", "今天整理照片。");
        DiaryImage first = image(101, 21, "paris-1.jpg", 1);
        DiaryImage second = image(102, 21, "paris-2.jpg", 2);

        Album existingAiAlbum = new Album();
        existingAiAlbum.setAlbumId(9);
        existingAiAlbum.setUserId(3);
        existingAiAlbum.setType("AI");
        existingAiAlbum.setName("欧洲旅行");

        when(aiConfigService.getRuntimeConfig()).thenReturn(new AiRuntimeConfig("https://api.example.com/v1", "sk", "gpt-test", 30));
        when(diaryMapper.findDiariesForReport(3, Date.valueOf("2026-05-01"), Date.valueOf("2026-05-31")))
                .thenReturn(Arrays.asList(travel, note));
        when(diaryImageMapper.findDiaryImagesByDiaryIds(Arrays.asList(21, 22))).thenReturn(Arrays.asList(first, second));
        when(albumMapper.findAiAlbumsByUserId(3)).thenReturn(Collections.singletonList(existingAiAlbum));
        when(aiClient.generate(any(), any())).thenReturn("{\"albums\":[{\"mode\":\"MERGE\",\"targetAlbumId\":9,\"title\":\"欧洲旅行\",\"description\":\"巴黎段回忆\",\"diaryIds\":[21]}]}");

        AiAlbumProposalRequestDTO request = new AiAlbumProposalRequestDTO();
        request.setStartDate("2026-05-01");
        request.setEndDate("2026-05-31");
        request.setPrompt("重点整理欧洲旅游");

        proposalService.generate(3, request);

        ArgumentCaptor<AiAlbumProposal> captor = ArgumentCaptor.forClass(AiAlbumProposal.class);
        verify(proposalMapper).insert(captor.capture());
        AiAlbumProposal saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getContentJson()).contains("\"targetAlbumId\":9", "\"imageIds\":[101,102]");
        assertThat(saved.getModel()).isEqualTo("gpt-test");
        verify(albumMapper, never()).insertAlbum(any());
        verify(albumMapper, never()).insertAlbumPhotos(any(), any());
    }

    @Test
    void confirmProposalCreatesNewAlbumsAndIndexesPhotos() {
        AiAlbumProposal proposal = new AiAlbumProposal();
        proposal.setProposalId(5);
        proposal.setUserId(3);
        proposal.setStatus("PENDING");
        proposal.setContentJson("{\"albums\":[{\"mode\":\"NEW\",\"title\":\"欧洲旅行\",\"description\":\"五月旅行\",\"imageIds\":[101,102]}]}");

        when(proposalMapper.findById(3, 5)).thenReturn(proposal);
        when(photoMapper.findPhotosByIds(3, Arrays.asList(101, 102)))
                .thenReturn(Arrays.asList(photo(101), photo(102)));
        when(albumMapper.ensureAiGroup(3)).thenReturn(aiGroup(8));
        doAnswer(invocation -> {
            Album album = invocation.getArgument(0);
            album.setAlbumId(30);
            return null;
        }).when(albumMapper).insertAlbum(any());

        proposalService.confirm(3, 5);

        ArgumentCaptor<Album> albumCaptor = ArgumentCaptor.forClass(Album.class);
        verify(albumMapper).insertAlbum(albumCaptor.capture());
        assertThat(albumCaptor.getValue().getGroupId()).isEqualTo(8);
        assertThat(albumCaptor.getValue().getType()).isEqualTo("AI");
        assertThat(albumCaptor.getValue().getName()).isEqualTo("欧洲旅行");
        verify(albumMapper).insertAlbumPhotos(30, Arrays.asList(101, 102));
        verify(proposalMapper).updateStatus(3, 5, "CONFIRMED");
    }

    @Test
    void confirmProposalRejectsImagesOwnedByAnotherUser() {
        AiAlbumProposal proposal = new AiAlbumProposal();
        proposal.setProposalId(5);
        proposal.setUserId(3);
        proposal.setStatus("PENDING");
        proposal.setContentJson("{\"albums\":[{\"mode\":\"NEW\",\"title\":\"混入照片\",\"imageIds\":[101,999]}]}");

        when(proposalMapper.findById(3, 5)).thenReturn(proposal);
        when(photoMapper.findPhotosByIds(3, Arrays.asList(101, 999)))
                .thenReturn(Collections.singletonList(photo(101)));

        assertThatThrownBy(() -> proposalService.confirm(3, 5))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND.getCode()));

        verify(albumMapper, never()).insertAlbum(any());
        verify(albumMapper, never()).insertAlbumPhotos(any(), any());
    }

    private Diary diary(Integer id, String date, String title, String content) {
        Diary diary = new Diary();
        diary.setDiaryId(id);
        diary.setUserId(3);
        diary.setDate(Date.valueOf(date));
        diary.setTitle(title);
        diary.setContent(content);
        return diary;
    }

    private DiaryImage image(Integer imageId, Integer diaryId, String imagePath, Integer sort) {
        DiaryImage image = new DiaryImage();
        image.setImageId(imageId);
        image.setDiaryId(diaryId);
        image.setImagePath(imagePath);
        image.setSort(sort);
        return image;
    }

    private com.langxi.babydiary.entity.AlbumGroup aiGroup(Integer groupId) {
        com.langxi.babydiary.entity.AlbumGroup group = new com.langxi.babydiary.entity.AlbumGroup();
        group.setGroupId(groupId);
        group.setUserId(3);
        group.setType("AI");
        group.setName("AI 整理");
        return group;
    }

    private Photo photo(Integer imageId) {
        Photo photo = new Photo();
        photo.setImageId(imageId);
        photo.setUserId(3);
        return photo;
    }
}

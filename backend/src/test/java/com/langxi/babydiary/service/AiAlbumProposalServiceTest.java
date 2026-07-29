package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.AiAlbumProposalRequestDTO;
import com.langxi.babydiary.dto.MediaAssetVO;
import com.langxi.babydiary.entity.AiAlbumProposal;
import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.AlbumGroup;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiAlbumProposalMapper;
import com.langxi.babydiary.mapper.AlbumMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAlbumProposalServiceTest {
    private static final String FIRST = "11111111-1111-1111-1111-111111111111";
    private static final String SECOND = "22222222-2222-2222-2222-222222222222";
    private static final String FOREIGN = "99999999-9999-9999-9999-999999999999";

    @Mock private AiConfigService aiConfigService;
    @Mock private OpenAiCompatibleClient aiClient;
    @Mock private DiaryMapper diaryMapper;
    @Mock private MediaService mediaService;
    @Mock private AlbumMapper albumMapper;
    @Mock private PhotoMapper photoMapper;
    @Mock private AiAlbumProposalMapper proposalMapper;
    @InjectMocks private AiAlbumProposalService service;

    @Test
    void generateStoresAssetIdsWithoutCreatingAlbums() {
        Diary travel = diary(21, "巴黎第一天");
        Diary note = diary(22, "随手记");
        Album existing = new Album();
        existing.setAlbumId(9);
        existing.setType("AI");
        existing.setName("欧洲旅行");
        when(aiConfigService.getRuntimeConfig()).thenReturn(
                new AiRuntimeConfig("https://api.example.com/v1", "sk", "gpt-test", 30));
        when(diaryMapper.findDiariesForReport(3, Date.valueOf("2026-05-01"), Date.valueOf("2026-05-31")))
                .thenReturn(List.of(travel, note));
        when(mediaService.findByDiaries(List.of(21, 22))).thenReturn(Map.of(21, List.of(media(FIRST), media(SECOND)), 22, List.of()));
        when(albumMapper.findAiAlbumsByUserId(3)).thenReturn(List.of(existing));
        when(aiClient.generate(any(), any())).thenReturn(
                "{\"albums\":[{\"mode\":\"MERGE\",\"targetAlbumId\":9,\"title\":\"欧洲旅行\",\"diaryIds\":[21]}]}");

        AiAlbumProposalRequestDTO request = new AiAlbumProposalRequestDTO();
        request.setStartDate("2026-05-01");
        request.setEndDate("2026-05-31");
        service.generate(3, request);

        ArgumentCaptor<AiAlbumProposal> captor = ArgumentCaptor.forClass(AiAlbumProposal.class);
        verify(proposalMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentJson()).contains("\"assetIds\"", FIRST, SECOND).doesNotContain("imageIds");
        verify(albumMapper, never()).insertAlbum(any());
    }

    @Test
    void confirmCreatesAlbumAndAddsAssets() {
        AiAlbumProposal proposal = proposal("{\"albums\":[{\"mode\":\"NEW\",\"title\":\"欧洲旅行\",\"assetIds\":[\"" + FIRST + "\",\"" + SECOND + "\"]}]}");
        when(proposalMapper.findById(3, 5)).thenReturn(proposal);
        when(photoMapper.findPhotosByIds(3, List.of(FIRST, SECOND))).thenReturn(List.of(photo(FIRST), photo(SECOND)));
        when(albumMapper.ensureAiGroup(3)).thenReturn(group());
        when(albumMapper.nextAlbumPhotoSort(30)).thenReturn(7);
        doAnswer(invocation -> { ((Album) invocation.getArgument(0)).setAlbumId(30); return null; })
                .when(albumMapper).insertAlbum(any());

        service.confirm(3, 5);

        verify(albumMapper).insertAlbumPhoto(30, 3, FIRST, 7);
        verify(albumMapper).insertAlbumPhoto(30, 3, SECOND, 8);
        verify(proposalMapper).updateStatus(3, 5, "CONFIRMED");
    }

    @Test
    void confirmRejectsAssetsNotOwnedByCurrentUser() {
        AiAlbumProposal proposal = proposal("{\"albums\":[{\"mode\":\"NEW\",\"title\":\"混入照片\",\"assetIds\":[\"" + FIRST + "\",\"" + FOREIGN + "\"]}]}");
        when(proposalMapper.findById(3, 5)).thenReturn(proposal);
        when(photoMapper.findPhotosByIds(3, List.of(FIRST, FOREIGN))).thenReturn(List.of(photo(FIRST)));

        assertThatThrownBy(() -> service.confirm(3, 5))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND.getCode()));
        verify(albumMapper, never()).insertAlbum(any());
    }

    private Diary diary(int id, String title) {
        Diary diary = new Diary();
        diary.setDiaryId(id);
        diary.setUserId(3);
        diary.setDate(Date.valueOf("2026-05-02"));
        diary.setTitle(title);
        diary.setContent("content");
        return diary;
    }

    private MediaAssetVO media(String id) {
        MediaAssetVO media = new MediaAssetVO();
        media.setAssetId(id);
        media.setMediaType("IMAGE");
        return media;
    }

    private Photo photo(String id) {
        Photo photo = new Photo();
        photo.setAssetId(id);
        photo.setUserId(3);
        return photo;
    }

    private AiAlbumProposal proposal(String json) {
        AiAlbumProposal proposal = new AiAlbumProposal();
        proposal.setProposalId(5);
        proposal.setUserId(3);
        proposal.setStatus("PENDING");
        proposal.setContentJson(json);
        return proposal;
    }

    private AlbumGroup group() {
        AlbumGroup group = new AlbumGroup();
        group.setGroupId(8);
        group.setUserId(3);
        group.setType("AI");
        return group;
    }
}

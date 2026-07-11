package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.DiaryWriteDTO;
import com.langxi.babydiary.dto.ImportResultVO;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class PortableArchiveServiceTest {
    @Mock private SpaceService spaceService;
    @Mock private CollaborationMapper collaborationMapper;
    @Mock private DiaryImageMapper imageMapper;
    @Mock private TagService tagService;
    @Mock private CollaborativeDiaryService diaryService;
    @Mock private ImageStorageService imageStorageService;
    @Mock private AccountSecurityService accountSecurityService;
    @Mock private MediaService mediaService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private PortableArchiveService service;

    @Test
    void importRejectsZipTraversalBeforeWritingFiles() throws Exception {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        MockMultipartFile archive = new MockMultipartFile(
                "archive", "invalid.zip", "application/zip", zip("../outside.txt", "bad"));

        assertThatThrownBy(() -> service.importSpace("space-one", 2, archive, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    void importRemapsDiaryIdsThatAlreadyExistInAnotherSpace() throws Exception {
        String archivedId = "33b6732e-1de9-444b-95d4-734722051ff6";
        DiarySpace target = new DiarySpace();
        target.setSpaceId(7L);
        target.setPublicId("target-space");
        when(spaceService.requireSpace("target-space")).thenReturn(target);
        when(collaborationMapper.findDiary(7L, archivedId)).thenReturn(null);
        when(collaborationMapper.findDiaryByPublicId(archivedId)).thenReturn(new Diary());

        String remapped = UUID.nameUUIDFromBytes(
                ("target-space:" + archivedId).getBytes(StandardCharsets.UTF_8)).toString();
        Diary imported = new Diary();
        imported.setDiaryId(42);
        when(collaborationMapper.findDiary(7L, remapped)).thenReturn(null, imported);

        String manifest = """
                {"version":2,"diaries":[{"publicId":"%s","title":"导入日记","date":"2026-07-11",
                "content":"导入内容","contentFormat":"html","visibility":"PRIVATE","locked":false}]}
                """.formatted(archivedId);
        MockMultipartFile archive = new MockMultipartFile(
                "archive", "portable.zip", "application/zip", zip("manifest.json", manifest));

        ImportResultVO result = service.importSpace("target-space", 2, archive, null);

        ArgumentCaptor<DiaryWriteDTO> diary = ArgumentCaptor.forClass(DiaryWriteDTO.class);
        verify(diaryService).create(eq("target-space"), eq(2), diary.capture(), eq(null));
        assertThat(diary.getValue().getClientId()).isEqualTo(remapped);
        assertThat(result.getImportedDiaries()).isEqualTo(1);
        assertThat(result.getSkippedDiaries()).isZero();
    }

    @Test
    void lockedLegacyImagesAreImportedIntoPrivateMediaStorage() throws Exception {
        String archivedId = "33b6732e-1de9-444b-95d4-734722051ff6";
        DiarySpace target = new DiarySpace();
        target.setSpaceId(7L);
        target.setPublicId("target-space");
        when(spaceService.requireSpace("target-space")).thenReturn(target);
        Diary imported = new Diary();
        imported.setDiaryId(42);
        when(collaborationMapper.findDiary(7L, archivedId)).thenReturn(null, imported);

        String imagePath = "media/" + archivedId + "/1.jpg";
        String manifest = """
                {"version":2,"diaries":[{"publicId":"%s","title":"锁定日记","date":"2026-07-11",
                "content":"导入内容","contentFormat":"html","visibility":"PRIVATE","locked":true,
                "images":["%s"]}]}
                """.formatted(archivedId, imagePath);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
        entries.put(imagePath, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        MockMultipartFile archive = new MockMultipartFile(
                "archive", "portable.zip", "application/zip", zip(entries));

        ImportResultVO result = service.importSpace("target-space", 2, archive, "step-up");

        verify(mediaService).upload(eq("target-space"), eq(2), any(PathMultipartFile.class),
                eq(archivedId), isNull(), isNull(), isNull(), isNull(), isNull(), eq("step-up"));
        verify(imageStorageService, never()).storeImageBytes(any(), any(), any(), any(Boolean.class));
        assertThat(result.getImportedImages()).isZero();
        assertThat(result.getImportedMedia()).isEqualTo(1);
    }

    private byte[] zip(String name, String value) throws Exception {
        return zip(Map.of(name, value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}

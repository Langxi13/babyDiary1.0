package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.ImportResultVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.PortableArchiveService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/transfer")
public class DataTransferController {
    private final PortableArchiveService archiveService;
    private final CurrentUser currentUser;

    public DataTransferController(PortableArchiveService archiveService, CurrentUser currentUser) {
        this.archiveService = archiveService;
        this.currentUser = currentUser;
    }

    @GetMapping("/export")
    public ResponseEntity<FileSystemResource> exportSpace(
            @PathVariable String spaceId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) throws IOException {
        FileSystemResource archive = archiveService.exportSpace(spaceId, currentUser.getUserId(), stepUpToken);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Baby-Diary-export.zip")
                .body(archive);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResultVO> importSpace(
            @PathVariable String spaceId,
            @RequestParam("archive") MultipartFile archive,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) throws IOException {
        return Result.success("导入完成", archiveService.importSpace(
                spaceId, currentUser.getUserId(), archive, stepUpToken));
    }
}

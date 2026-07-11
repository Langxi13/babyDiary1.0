package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.MediaAssetVO;
import com.langxi.babydiary.dto.MediaMetadataDTO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.MediaService;
import com.langxi.babydiary.storage.StoredObject;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

@RestController
public class MediaController {
    private final MediaService mediaService;
    private final CurrentUser currentUser;

    public MediaController(MediaService mediaService, CurrentUser currentUser) {
        this.mediaService = mediaService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/api/v2/spaces/{spaceId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MediaAssetVO> upload(@PathVariable String spaceId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String diaryId,
                                       @RequestParam(required = false) String caption,
                                       @RequestParam(required = false) String takenAt,
                                       @RequestParam(required = false) String locationName,
                                       @RequestParam(required = false) BigDecimal latitude,
                                       @RequestParam(required = false) BigDecimal longitude,
                                       @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) throws IOException {
        return Result.success("媒体已上传，正在处理", mediaService.upload(spaceId, currentUser.getUserId(), file,
                diaryId, caption, takenAt, locationName, latitude, longitude, stepUpToken));
    }

    @GetMapping("/api/v2/spaces/{spaceId}/media/{assetId}")
    public Result<MediaAssetVO> detail(@PathVariable String spaceId,
                                       @PathVariable String assetId,
                                       @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success(mediaService.toVO(mediaService.requireAccessible(
                spaceId, assetId, currentUser.getUserId(), stepUpToken)));
    }

    @PutMapping("/api/v2/spaces/{spaceId}/media/{assetId}")
    public Result<MediaAssetVO> updateMetadata(@PathVariable String spaceId,
                                               @PathVariable String assetId,
                                               @Valid @RequestBody MediaMetadataDTO dto,
                                               @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success("媒体信息已更新", mediaService.updateMetadata(
                spaceId, assetId, currentUser.getUserId(), dto, stepUpToken));
    }

    @DeleteMapping("/api/v2/spaces/{spaceId}/media/{assetId}")
    public Result<Void> delete(@PathVariable String spaceId,
                               @PathVariable String assetId,
                               @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        mediaService.delete(spaceId, assetId, currentUser.getUserId(), stepUpToken);
        return Result.success("媒体已删除", null);
    }

    @GetMapping("/api/v2/media/public/{assetId}/{variant}")
    public ResponseEntity<InputStreamResource> content(@PathVariable String assetId,
                                                       @PathVariable String variant,
                                                       @RequestParam long expires,
                                                       @RequestParam String signature) throws IOException {
        StoredObject object = mediaService.openSigned(assetId, variant, expires, signature);
        String contentType = object.contentType() == null
                ? mediaService.contentType(assetId, variant) : object.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .contentLength(object.length())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(15)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(object.stream()));
    }
}

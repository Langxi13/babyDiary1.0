package com.langxi.babydiary.v3.media.api;

import com.langxi.babydiary.storage.StoredObject;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaService;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/media")
public class MediaController {
    private final MediaService media;
    private final MediaUrlSigner urls;

    public MediaController(MediaService media, MediaUrlSigner urls) {
        this.media = media;
        this.urls = urls;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> upload(@AuthenticationPrincipal V3Principal principal,
                                                 @PathVariable UUID spaceId,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) @Size(max = 500) String caption,
                                                 @RequestParam(required = false) LocalDateTime takenAt) throws IOException {
        MediaAsset asset = media.upload(spaceId, principal.accountId(), file, caption, takenAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.from(asset, spaceId, urls));
    }

    @GetMapping
    public MediaPageResponse list(@AuthenticationPrincipal V3Principal principal,
                                  @PathVariable UUID spaceId,
                                  @RequestParam(required = false) String mediaType,
                                  @RequestParam(defaultValue = "true") boolean libraryOnly,
                                  @RequestParam(defaultValue = "30") int size,
                                  @RequestParam(required = false) String cursor) {
        MediaService.Page page = media.page(spaceId, principal.accountId(),
                new MediaService.Query(mediaType, libraryOnly, cursor, size));
        return new MediaPageResponse(page.items().stream().map(asset -> MediaResponse.from(asset, spaceId, urls)).toList(),
                page.nextCursor());
    }

    @GetMapping("/{assetId}")
    public MediaResponse detail(@AuthenticationPrincipal V3Principal principal,
                                @PathVariable UUID spaceId, @PathVariable UUID assetId) {
        return MediaResponse.from(media.detail(spaceId, assetId, principal.accountId()), spaceId, urls);
    }

    @GetMapping("/{assetId}/variants/{variant}")
    public ResponseEntity<StreamingResponseBody> content(@AuthenticationPrincipal V3Principal principal,
                                                          @PathVariable UUID spaceId, @PathVariable UUID assetId,
                                                          @PathVariable String variant,
                                                          @RequestParam(required = false) String profile) throws IOException {
        StoredObject object = media.openVariant(spaceId, assetId, variant, profile, principal.accountId());
        String contentType = object.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : object.contentType();
        StreamingResponseBody body = output -> {
            try (object) {
                object.stream().transferTo(output);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .contentLength(object.length()).cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(15)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").body(body);
    }

    @DeleteMapping("/{assetId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal V3Principal principal,
                       @PathVariable UUID spaceId, @PathVariable UUID assetId) {
        media.delete(spaceId, assetId, principal.accountId());
    }

    @PutMapping("/{assetId}")
    public MediaResponse update(@AuthenticationPrincipal V3Principal principal,@PathVariable UUID spaceId,
                                @PathVariable UUID assetId,@Valid @RequestBody MetadataRequest request) {
        return MediaResponse.from(media.update(spaceId,assetId,principal.accountId(),request.caption(),
                request.takenAt(),request.accessScope(),request.libraryVisible()),spaceId,urls);
    }

    public record MediaPageResponse(List<MediaResponse> items, String nextCursor) {
    }

    public record MetadataRequest(@Size(max=500) String caption,LocalDateTime takenAt,String accessScope,
                                  boolean libraryVisible) {}

    public record MediaResponse(UUID id, UUID spaceId, String mediaType, String originalFilename,
                                String caption, LocalDateTime takenAt, String accessScope,
                                boolean libraryVisible, String status, LocalDateTime createdAt,
                                List<VariantResponse> variants) {
        public static MediaResponse from(MediaAsset asset, UUID spaceId, MediaUrlSigner urls) {
            return new MediaResponse(asset.id(), spaceId, asset.mediaType(), asset.originalFilename(), asset.caption(),
                    asset.takenAt(), asset.accessScope(), asset.libraryVisible(), asset.status(), asset.createdAt(),
                    asset.variants().stream().map(value -> new VariantResponse(value.type(), value.profile(),
                            urls.url(spaceId, asset.id(), value.type(), value.profile()),
                            value.contentType(), value.sizeBytes(), value.width(), value.height(), value.status())).toList());
        }
    }

    public record VariantResponse(String type, String profile, String contentUrl, String contentType,
                                  long sizeBytes, Integer width, Integer height, String status) {
    }
}

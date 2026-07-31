package com.langxi.babydiary.media.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.media.application.MediaService;
import com.langxi.babydiary.media.application.MediaView;
import com.langxi.babydiary.platform.api.ApiContract;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping((ApiContract.ROOT + "/spaces/{spaceId}/media"))
public class MediaController {
    private final MediaService media;
    private final MediaRepresentationService representations;
    private final StepUpService stepUp;

    public MediaController(
            MediaService media, MediaRepresentationService representations, StepUpService stepUp) {
        this.media = media;
        this.representations = representations;
        this.stepUp = stepUp;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaView> upload(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 500) String caption,
            @RequestParam(required = false) LocalDateTime takenAt)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        representations.view(
                                media.upload(
                                        spaceId, principal.accountId(), file, caption, takenAt),
                                MediaAccessContext.direct(principal.accountId(), false)));
    }

    @GetMapping
    public MediaPageResponse list(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String mediaType,
            @RequestParam(defaultValue = "true") boolean libraryOnly,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        MediaService.Page page =
                media.page(
                        spaceId,
                        principal.accountId(),
                        new MediaService.Query(mediaType, libraryOnly, cursor, size));
        return new MediaPageResponse(
                representations.views(
                        page.items(), MediaAccessContext.direct(principal.accountId(), elevated)),
                page.nextCursor());
    }

    @GetMapping("/{assetId}")
    public MediaView detail(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        return representations.view(
                media.detail(spaceId, assetId, principal.accountId(), elevated),
                MediaAccessContext.direct(principal.accountId(), elevated));
    }

    @RequestMapping(
            value = "/{assetId}/variants/{variant}",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<StreamingResponseBody> content(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @PathVariable String variant,
            @RequestParam(required = false) String profile,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            HttpServletRequest request) {
        boolean elevated = stepUp.valid(principal, stepToken);
        MediaService.ResolvedVariant resolved =
                media.resolveVariant(
                        spaceId, assetId, variant, profile, principal.accountId(), elevated);
        return MediaContentResponse.create(
                media, resolved, range, ifNoneMatch, "HEAD".equals(request.getMethod()), elevated);
    }

    @DeleteMapping("/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        media.delete(spaceId, assetId, principal.accountId(), stepUp.valid(principal, stepToken));
    }

    @PutMapping("/{assetId}")
    public MediaView update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @Valid @RequestBody MetadataRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        return representations.view(
                media.update(
                        spaceId,
                        assetId,
                        principal.accountId(),
                        request.caption(),
                        request.takenAt(),
                        request.accessScope(),
                        request.libraryVisible(),
                        elevated),
                MediaAccessContext.direct(principal.accountId(), elevated));
    }

    @PostMapping("/{assetId}/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwnership(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        media.transferOwnership(
                spaceId,
                assetId,
                request.targetAccountId(),
                principal.accountId(),
                stepUp.valid(principal, stepToken));
    }

    public record MediaPageResponse(List<MediaView> items, String nextCursor) {}

    public record MetadataRequest(
            @Size(max = 500) String caption,
            LocalDateTime takenAt,
            @Pattern(regexp = "LINKED|SPACE") String accessScope,
            boolean libraryVisible) {}

    public record TransferRequest(UUID targetAccountId) {}
}

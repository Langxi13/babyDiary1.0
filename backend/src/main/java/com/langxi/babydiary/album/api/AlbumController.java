package com.langxi.babydiary.album.api;

import com.langxi.babydiary.album.application.AlbumQueryService;
import com.langxi.babydiary.album.application.AlbumService;
import com.langxi.babydiary.album.domain.AlbumCatalog;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.media.application.MediaView;
import com.langxi.babydiary.platform.api.ApiContract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping((ApiContract.ROOT + "/spaces/{spaceId}"))
public class AlbumController {
    private final AlbumQueryService queries;
    private final AlbumService albums;
    private final MediaRepresentationService media;
    private final StepUpService stepUp;

    public AlbumController(
            AlbumQueryService queries,
            AlbumService albums,
            MediaRepresentationService media,
            StepUpService stepUp) {
        this.queries = queries;
        this.albums = albums;
        this.media = media;
        this.stepUp = stepUp;
    }

    @GetMapping("/album-groups")
    public AlbumCatalogResponse catalog(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        return AlbumCatalogResponse.from(
                queries.catalog(spaceId, principal.accountId(), elevated),
                media,
                principal.accountId(),
                elevated);
    }

    @GetMapping("/albums/system/{key}")
    public AlbumDetailResponse system(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        return AlbumDetailResponse.from(
                queries.systemDetail(spaceId, key, principal.accountId(), page, size, elevated),
                media,
                MediaAccessContext.direct(principal.accountId(), elevated),
                page,
                size);
    }

    @GetMapping("/albums/{albumId}")
    public AlbumDetailResponse detail(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID albumId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        return AlbumDetailResponse.from(
                queries.detail(spaceId, albumId, principal.accountId(), page, size, elevated),
                media,
                MediaAccessContext.album(principal.accountId(), albumId, elevated),
                page,
                size);
    }

    @PostMapping("/album-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody GroupRequest request) {
        return GroupResponse.from(
                albums.createGroup(spaceId, principal.accountId(), request.name()),
                media,
                principal.accountId(),
                false);
    }

    @PutMapping("/album-groups/{groupId}")
    public GroupResponse updateGroup(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupRequest request) {
        return GroupResponse.from(
                albums.updateGroup(spaceId, groupId, principal.accountId(), request.name()),
                media,
                principal.accountId(),
                false);
    }

    @DeleteMapping("/album-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID groupId) {
        albums.deleteGroup(spaceId, groupId, principal.accountId());
    }

    @PostMapping("/albums")
    @ResponseStatus(HttpStatus.CREATED)
    public AlbumResponse createAlbum(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody AlbumRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        AlbumCatalog.Album album =
                albums.createAlbum(
                        spaceId,
                        principal.accountId(),
                        request.groupId(),
                        request.name(),
                        request.description(),
                        request.mediaIds(),
                        elevated);
        return AlbumResponse.from(
                album,
                media,
                MediaAccessContext.album(principal.accountId(), album.id(), elevated));
    }

    @PutMapping("/albums/{albumId}")
    public AlbumResponse updateAlbum(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID albumId,
            @Valid @RequestBody AlbumRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        albums.updateAlbum(
                spaceId,
                albumId,
                principal.accountId(),
                request.groupId(),
                request.name(),
                request.description());
        return AlbumResponse.from(
                queries.detail(spaceId, albumId, principal.accountId(), 0, 1, elevated).album(),
                media,
                MediaAccessContext.album(principal.accountId(), albumId, elevated));
    }

    @DeleteMapping("/albums/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlbum(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID albumId) {
        albums.deleteAlbum(spaceId, albumId, principal.accountId());
    }

    @PostMapping("/albums/{albumId}/media")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMedia(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID albumId,
            @Valid @RequestBody MediaIdsRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        albums.addMedia(
                spaceId,
                albumId,
                principal.accountId(),
                request.mediaIds(),
                stepUp.valid(principal, token));
    }

    @DeleteMapping("/albums/{albumId}/media/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMedia(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID albumId,
            @PathVariable UUID assetId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        albums.removeMedia(
                spaceId, albumId, assetId, principal.accountId(), stepUp.valid(principal, token));
    }

    @PutMapping("/media/{assetId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        albums.favorite(
                spaceId, assetId, principal.accountId(), true, stepUp.valid(principal, token));
    }

    @DeleteMapping("/media/{assetId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        albums.favorite(
                spaceId, assetId, principal.accountId(), false, stepUp.valid(principal, token));
    }

    public record GroupRequest(@NotBlank @Size(max = 100) String name) {}

    public record AlbumRequest(
            UUID groupId,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description,
            @Size(max = 200) List<UUID> mediaIds) {}

    public record MediaIdsRequest(@Size(max = 200) List<UUID> mediaIds) {}

    public record AlbumCatalogResponse(List<GroupResponse> groups) {
        static AlbumCatalogResponse from(
                AlbumCatalog catalog,
                MediaRepresentationService media,
                long accountId,
                boolean elevated) {
            return new AlbumCatalogResponse(
                    catalog.groups().stream()
                            .map(group -> GroupResponse.from(group, media, accountId, elevated))
                            .toList());
        }
    }

    public record GroupResponse(UUID id, String type, String name, List<AlbumResponse> albums) {
        static GroupResponse from(
                AlbumCatalog.Group group,
                MediaRepresentationService media,
                long accountId,
                boolean elevated) {
            return new GroupResponse(
                    group.id(),
                    group.type(),
                    group.name(),
                    group.albums().stream()
                            .map(
                                    album ->
                                            AlbumResponse.from(
                                                    album,
                                                    media,
                                                    album.id() == null
                                                            ? MediaAccessContext.direct(
                                                                    accountId, elevated)
                                                            : MediaAccessContext.album(
                                                                    accountId,
                                                                    album.id(),
                                                                    elevated)))
                            .toList());
        }
    }

    public record AlbumResponse(
            UUID id,
            UUID groupId,
            String systemKey,
            String type,
            String name,
            String description,
            UUID coverAssetId,
            MediaView coverMedia,
            long mediaCount) {
        static AlbumResponse from(
                AlbumCatalog.Album album,
                MediaRepresentationService media,
                MediaAccessContext context) {
            return new AlbumResponse(
                    album.id(),
                    album.groupId(),
                    album.systemKey(),
                    album.type(),
                    album.name(),
                    album.description(),
                    album.coverAssetId(),
                    album.coverMedia() == null ? null : media.view(album.coverMedia(), context),
                    album.mediaCount());
        }
    }

    public record AlbumDetailResponse(
            AlbumResponse album,
            List<MediaView> media,
            long totalMedia,
            int pageNumber,
            int pageSize,
            int totalPages) {
        static AlbumDetailResponse from(
                AlbumCatalog.Detail detail,
                MediaRepresentationService media,
                MediaAccessContext context,
                int page,
                int size) {
            int normalizedSize = Math.max(1, Math.min(size, 60));
            return new AlbumDetailResponse(
                    AlbumResponse.from(detail.album(), media, context),
                    media.views(detail.media(), context),
                    detail.totalMedia(),
                    Math.max(0, page),
                    normalizedSize,
                    (int) Math.ceil((double) detail.totalMedia() / normalizedSize));
        }
    }
}

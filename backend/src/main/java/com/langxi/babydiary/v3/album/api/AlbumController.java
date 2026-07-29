package com.langxi.babydiary.v3.album.api;

import com.langxi.babydiary.v3.album.application.AlbumService;
import com.langxi.babydiary.v3.album.domain.AlbumCatalog;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.api.MediaController;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}")
public class AlbumController {
    private final AlbumService albums;
    private final MediaUrlSigner urls;

    public AlbumController(AlbumService albums, MediaUrlSigner urls) {
        this.albums = albums;
        this.urls = urls;
    }

    @GetMapping("/album-groups")
    public AlbumCatalogResponse catalog(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId) {
        return AlbumCatalogResponse.from(albums.catalog(spaceId, principal.accountId()), spaceId, urls);
    }

    @GetMapping("/albums/system/{key}")
    public AlbumDetailResponse system(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                      @PathVariable String key, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "24") int size) {
        return AlbumDetailResponse.from(albums.systemDetail(spaceId, key, principal.accountId(), page, size),
                spaceId, urls, page, size);
    }

    @GetMapping("/albums/{albumId}")
    public AlbumDetailResponse detail(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                      @PathVariable UUID albumId, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "24") int size) {
        return AlbumDetailResponse.from(albums.detail(spaceId, albumId, principal.accountId(), page, size),
                spaceId, urls, page, size);
    }

    @PostMapping("/album-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                     @Valid @RequestBody GroupRequest request) {
        return GroupResponse.from(albums.createGroup(spaceId, principal.accountId(), request.name()), spaceId, urls);
    }

    @PutMapping("/album-groups/{groupId}")
    public GroupResponse updateGroup(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                     @PathVariable UUID groupId, @Valid @RequestBody GroupRequest request) {
        return GroupResponse.from(albums.updateGroup(spaceId, groupId, principal.accountId(), request.name()),
                spaceId, urls);
    }

    @DeleteMapping("/album-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                            @PathVariable UUID groupId) {
        albums.deleteGroup(spaceId, groupId, principal.accountId());
    }

    @PostMapping("/albums")
    @ResponseStatus(HttpStatus.CREATED)
    public AlbumResponse createAlbum(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                     @Valid @RequestBody AlbumRequest request) {
        return AlbumResponse.from(albums.createAlbum(spaceId, principal.accountId(), request.groupId(), request.name(),
                request.description(), request.mediaIds()), spaceId, urls);
    }

    @PutMapping("/albums/{albumId}")
    public AlbumResponse updateAlbum(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                     @PathVariable UUID albumId, @Valid @RequestBody AlbumRequest request) {
        return AlbumResponse.from(albums.updateAlbum(spaceId, albumId, principal.accountId(), request.groupId(),
                request.name(), request.description()), spaceId, urls);
    }

    @DeleteMapping("/albums/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlbum(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                            @PathVariable UUID albumId) {
        albums.deleteAlbum(spaceId, albumId, principal.accountId());
    }

    @PostMapping("/albums/{albumId}/media")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMedia(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                         @PathVariable UUID albumId, @Valid @RequestBody MediaIdsRequest request) {
        albums.addMedia(spaceId, albumId, principal.accountId(), request.mediaIds());
    }

    @DeleteMapping("/albums/{albumId}/media/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMedia(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                            @PathVariable UUID albumId, @PathVariable UUID assetId) {
        albums.removeMedia(spaceId, albumId, assetId, principal.accountId());
    }

    @PutMapping("/media/{assetId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                         @PathVariable UUID assetId) {
        albums.favorite(spaceId, assetId, principal.accountId(), true);
    }

    @DeleteMapping("/media/{assetId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                           @PathVariable UUID assetId) {
        albums.favorite(spaceId, assetId, principal.accountId(), false);
    }

    public record GroupRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record AlbumRequest(UUID groupId, @NotBlank @Size(max = 100) String name,
                               @Size(max = 2000) String description, @Size(max = 200) List<UUID> mediaIds) {
    }

    public record MediaIdsRequest(@Size(max = 200) List<UUID> mediaIds) {
    }

    public record AlbumCatalogResponse(List<GroupResponse> groups) {
        static AlbumCatalogResponse from(AlbumCatalog catalog, UUID spaceId, MediaUrlSigner urls) {
            return new AlbumCatalogResponse(catalog.groups().stream()
                    .map(group -> GroupResponse.from(group, spaceId, urls)).toList());
        }
    }

    public record GroupResponse(UUID id, String type, String name, List<AlbumResponse> albums) {
        static GroupResponse from(AlbumCatalog.Group group, UUID spaceId, MediaUrlSigner urls) {
            return new GroupResponse(group.id(), group.type(), group.name(), group.albums().stream()
                    .map(album -> AlbumResponse.from(album, spaceId, urls)).toList());
        }
    }

    public record AlbumResponse(UUID id, UUID groupId, String systemKey, String type, String name,
                                String description, UUID coverAssetId, String coverContentUrl, long mediaCount) {
        static AlbumResponse from(AlbumCatalog.Album album, UUID spaceId, MediaUrlSigner urls) {
            return new AlbumResponse(album.id(), album.groupId(), album.systemKey(), album.type(), album.name(),
                    album.description(), album.coverAssetId(), album.coverAssetId() == null ? null
                    : urls.url(spaceId, album.coverAssetId(), "ORIGINAL"), album.mediaCount());
        }
    }

    public record AlbumDetailResponse(AlbumResponse album, List<MediaController.MediaResponse> media,
                                     long totalMedia, int pageNumber, int pageSize, int totalPages) {
        static AlbumDetailResponse from(AlbumCatalog.Detail detail, UUID spaceId, MediaUrlSigner urls,
                                        int page, int size) {
            int normalizedSize = Math.max(1, Math.min(size, 60));
            return new AlbumDetailResponse(AlbumResponse.from(detail.album(), spaceId, urls), detail.media().stream()
                    .map(media -> MediaController.MediaResponse.from(media, spaceId, urls)).toList(),
                    detail.totalMedia(), Math.max(0, page), normalizedSize,
                    (int) Math.ceil((double) detail.totalMedia() / normalizedSize));
        }
    }
}

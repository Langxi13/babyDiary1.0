package com.langxi.babydiary.home.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.langxi.babydiary.home.application.HomeProjection;
import com.langxi.babydiary.home.application.HomeService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.media.application.MediaView;
import com.langxi.babydiary.platform.api.ApiContract;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContract.ROOT + "/spaces/{spaceId}/home")
public class HomeController {
    private final HomeService homes;
    private final StepUpService stepUp;
    private final MediaRepresentationService media;

    public HomeController(
            HomeService homes, StepUpService stepUp, MediaRepresentationService media) {
        this.homes = homes;
        this.stepUp = stepUp;
        this.media = media;
    }

    @GetMapping
    public HomeResponse home(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        HomeProjection value = homes.home(spaceId, principal.accountId(), elevated);
        return HomeResponse.from(value, spaceId, principal.accountId(), elevated, media);
    }

    public record HomeResponse(
            long diaryTotal,
            List<DiaryResponse> recentDiaries,
            List<DraftResponse> drafts,
            List<AnniversaryResponse> anniversaries,
            List<FavoriteResponse> favorites) {
        static HomeResponse from(
                HomeProjection value,
                UUID spaceId,
                long accountId,
                boolean elevated,
                MediaRepresentationService media) {
            return new HomeResponse(
                    value.diaryTotal(),
                    value.recentDiaries().stream()
                            .map(
                                    item ->
                                            DiaryResponse.from(
                                                    item, spaceId, accountId, elevated, media))
                            .toList(),
                    value.drafts().stream().map(DraftResponse::from).toList(),
                    value.anniversaries().stream().map(AnniversaryResponse::from).toList(),
                    value.favorites().stream()
                            .map(
                                    item ->
                                            FavoriteResponse.from(
                                                    item, spaceId, accountId, elevated, media))
                            .toList());
        }
    }

    public record DiaryResponse(
            UUID id,
            String title,
            LocalDate diaryDate,
            String contentSnippet,
            String mood,
            String visibility,
            boolean locked,
            int version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<HomeProjection.Tag> tags,
            long mediaCount,
            List<MediaResponse> previews) {
        static DiaryResponse from(
                HomeProjection.Diary value,
                UUID spaceId,
                long accountId,
                boolean elevated,
                MediaRepresentationService media) {
            return new DiaryResponse(
                    value.id(),
                    value.title(),
                    value.diaryDate(),
                    value.contentSnippet(),
                    value.mood(),
                    value.visibility(),
                    value.locked(),
                    value.version(),
                    value.createdAt(),
                    value.updatedAt(),
                    value.tags(),
                    value.mediaCount(),
                    value.previews().stream()
                            .map(
                                    item ->
                                            MediaResponse.diary(
                                                    item,
                                                    value.id(),
                                                    spaceId,
                                                    accountId,
                                                    elevated,
                                                    media))
                            .toList());
        }
    }

    public record DraftResponse(
            UUID id, String draftKey, UUID diaryId, JsonNode payload, LocalDateTime updatedAt) {
        static DraftResponse from(HomeProjection.Draft value) {
            return new DraftResponse(
                    value.id(),
                    value.draftKey(),
                    value.diaryId(),
                    value.payload(),
                    value.updatedAt());
        }
    }

    public record AnniversaryResponse(UUID id, String title, LocalDate date) {
        static AnniversaryResponse from(HomeProjection.Anniversary value) {
            return new AnniversaryResponse(value.id(), value.title(), value.date());
        }
    }

    public record FavoriteResponse(UUID id, MediaResponse media, boolean favorite) {
        static FavoriteResponse from(
                HomeProjection.Favorite value,
                UUID spaceId,
                long accountId,
                boolean elevated,
                MediaRepresentationService media) {
            return new FavoriteResponse(
                    value.id(),
                    MediaResponse.favorite(value, spaceId, accountId, elevated, media),
                    true);
        }
    }

    public record MediaResponse(
            UUID id,
            String mediaType,
            int position,
            String status,
            boolean protectedContent,
            MediaView.Representations representations) {
        static MediaResponse diary(
                HomeProjection.Media value,
                UUID diaryId,
                UUID spaceId,
                long accountId,
                boolean elevated,
                MediaRepresentationService media) {
            boolean reveal = !value.protectedContent() || elevated;
            return new MediaResponse(
                    value.id(),
                    value.mediaType(),
                    value.position(),
                    value.status(),
                    value.protectedContent(),
                    media.links(
                            spaceId,
                            value.id(),
                            value.originalProfile(),
                            value.thumbnailProfile(),
                            value.previewProfile(),
                            MediaAccessContext.diary(accountId, diaryId, elevated),
                            reveal));
        }

        static MediaResponse favorite(
                HomeProjection.Favorite value,
                UUID spaceId,
                long accountId,
                boolean elevated,
                MediaRepresentationService media) {
            boolean reveal = !value.protectedContent() || elevated;
            return new MediaResponse(
                    value.id(),
                    value.mediaType(),
                    0,
                    value.status(),
                    value.protectedContent(),
                    media.links(
                            spaceId,
                            value.id(),
                            value.originalProfile(),
                            value.thumbnailProfile(),
                            value.previewProfile(),
                            MediaAccessContext.direct(accountId, elevated),
                            reveal));
        }
    }
}

package com.langxi.babydiary.anniversary.api;

import com.langxi.babydiary.anniversary.application.AnniversaryService;
import com.langxi.babydiary.anniversary.domain.Anniversary;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.media.application.MediaView;
import com.langxi.babydiary.platform.api.ApiContract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping((ApiContract.ROOT + "/spaces/{spaceId}/anniversaries"))
public class AnniversaryController {
    private final AnniversaryService anniversaries;
    private final MediaRepresentationService media;
    private final StepUpService stepUp;

    public AnniversaryController(
            AnniversaryService anniversaries,
            MediaRepresentationService media,
            StepUpService stepUp) {
        this.anniversaries = anniversaries;
        this.media = media;
        this.stepUp = stepUp;
    }

    @GetMapping
    public List<Response> list(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        return anniversaries.list(spaceId, principal.accountId()).stream()
                .map(item -> response(item, principal.accountId(), elevated))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody AnniversaryRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        return response(
                anniversaries.create(spaceId, principal.accountId(), request.command(), elevated),
                principal.accountId(),
                elevated);
    }

    @PutMapping("/{anniversaryId}")
    public Response update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID anniversaryId,
            @Valid @RequestBody AnniversaryRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        boolean elevated = stepUp.valid(principal, token);
        return response(
                anniversaries.update(
                        spaceId, anniversaryId, principal.accountId(), request.command(), elevated),
                principal.accountId(),
                elevated);
    }

    @DeleteMapping("/{anniversaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID anniversaryId) {
        anniversaries.delete(spaceId, anniversaryId, principal.accountId());
    }

    public record AnniversaryRequest(
            @NotBlank @Size(max = 100) String title,
            @NotNull LocalDate date,
            @Size(max = 5000) String description,
            UUID coverAssetId,
            int sortOrder) {
        AnniversaryService.Command command() {
            return new AnniversaryService.Command(
                    title, date, description, coverAssetId, sortOrder);
        }
    }

    private Response response(AnniversaryService.Item item, long accountId, boolean elevated) {
        Anniversary value = item.anniversary();
        MediaView cover =
                item.coverMedia() == null
                        ? null
                        : media.view(
                                item.coverMedia(),
                                MediaAccessContext.anniversary(accountId, value.id(), elevated));
        return new Response(
                value.id(),
                value.spaceId(),
                value.title(),
                value.date(),
                value.description(),
                value.coverAssetId(),
                cover,
                value.sortOrder(),
                value.createdAt(),
                value.updatedAt());
    }

    public record Response(
            UUID id,
            UUID spaceId,
            String title,
            LocalDate date,
            String description,
            UUID coverAssetId,
            MediaView coverMedia,
            int sortOrder,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt) {}
}

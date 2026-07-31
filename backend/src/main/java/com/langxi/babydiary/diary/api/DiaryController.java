package com.langxi.babydiary.diary.api;

import com.langxi.babydiary.diary.application.DiaryService;
import com.langxi.babydiary.diary.domain.DiaryEntry;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.media.application.MediaView;
import com.langxi.babydiary.platform.api.ApiContract;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.domain.CursorPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContract.ROOT + "/spaces/{spaceId}/diaries")
public class DiaryController {
    private final DiaryService diaries;
    private final MediaRepresentationService media;
    private final StepUpService stepUp;

    public DiaryController(
            DiaryService diaries, MediaRepresentationService media, StepUpService stepUp) {
        this.diaries = diaries;
        this.media = media;
        this.stepUp = stepUp;
    }

    @GetMapping
    public CursorResponse<DiaryResponse> list(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mood,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(defaultValue = "false") boolean trash,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "true") boolean includeTotal,
            @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        CursorPage<DiaryEntry> page =
                diaries.list(
                        spaceId,
                        principal.accountId(),
                        new DiaryService.ListQuery(
                                startDate,
                                endDate,
                                keyword,
                                mood,
                                tagId,
                                trash,
                                cursor,
                                size,
                                includeTotal),
                        elevated);
        return new CursorResponse<>(
                page.items().stream()
                        .map(
                                diary ->
                                        DiaryResponse.from(
                                                diary, media, principal.accountId(), elevated))
                        .toList(),
                page.nextCursor(),
                page.totalElements());
    }

    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> detail(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestParam(defaultValue = "false") boolean trash,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        DiaryEntry diary = diaries.detail(spaceId, diaryId, principal.accountId(), trash, elevated);
        return ResponseEntity.ok()
                .eTag(etag(diary.version()))
                .body(DiaryResponse.from(diary, media, principal.accountId(), elevated));
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody DiaryRequest request) {
        DiaryEntry diary = diaries.create(spaceId, principal.accountId(), request.command());
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(etag(diary.version()))
                .header(
                        HttpHeaders.LOCATION,
                        ApiContract.ROOT + "/spaces/" + spaceId + "/diaries/" + diary.id())
                .body(DiaryResponse.from(diary, media, principal.accountId(), false));
    }

    @PutMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken,
            @Valid @RequestBody DiaryRequest request) {
        int version = requiredVersion(ifMatch);
        boolean elevated = stepUp.valid(principal, stepToken);
        DiaryEntry diary =
                diaries.update(
                        spaceId,
                        diaryId,
                        principal.accountId(),
                        version,
                        request.command(),
                        elevated);
        return ResponseEntity.ok()
                .eTag(etag(diary.version()))
                .body(DiaryResponse.from(diary, media, principal.accountId(), elevated));
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        diaries.moveToTrash(
                spaceId,
                diaryId,
                principal.accountId(),
                requiredVersion(ifMatch),
                stepUp.valid(principal, stepToken));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{diaryId}/restore")
    public ResponseEntity<DiaryResponse> restore(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        DiaryEntry diary =
                diaries.restore(
                        spaceId,
                        diaryId,
                        principal.accountId(),
                        requiredVersion(ifMatch),
                        elevated);
        return ResponseEntity.ok()
                .eTag(etag(diary.version()))
                .body(DiaryResponse.from(diary, media, principal.accountId(), elevated));
    }

    @DeleteMapping("/{diaryId}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDelete(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        diaries.permanentlyDelete(
                spaceId,
                diaryId,
                principal.accountId(),
                requiredVersion(ifMatch),
                stepUp.valid(principal, stepToken));
    }

    @GetMapping("/{diaryId}/revisions")
    public List<DiaryService.RevisionView> revisions(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        return diaries.revisions(
                spaceId, diaryId, principal.accountId(), stepUp.valid(principal, stepToken));
    }

    @PostMapping("/{diaryId}/revisions/{revisionId}/restore")
    public ResponseEntity<DiaryResponse> restoreRevision(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @PathVariable UUID revisionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepToken) {
        boolean elevated = stepUp.valid(principal, stepToken);
        DiaryEntry diary =
                diaries.restoreRevision(
                        spaceId,
                        diaryId,
                        revisionId,
                        principal.accountId(),
                        requiredVersion(ifMatch),
                        elevated);
        return ResponseEntity.ok()
                .eTag(etag(diary.version()))
                .body(DiaryResponse.from(diary, media, principal.accountId(), elevated));
    }

    private int requiredVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED, "VERSION_REQUIRED", "编辑日记需要提供版本前置条件");
        }
        try {
            return Integer.parseInt(ifMatch.trim().replace("W/", "").replace("\"", ""));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERSION_INVALID", "版本前置条件无效");
        }
    }

    private String etag(int version) {
        return "\"" + version + "\"";
    }

    public record DiaryRequest(
            UUID clientId,
            @NotBlank @Size(max = 255) String title,
            @NotNull LocalDate diaryDate,
            @Size(max = 1_000_000) String contentHtml,
            @Size(max = 32) String mood,
            @Pattern(regexp = "PRIVATE|SHARED") String visibility,
            boolean locked,
            @Size(max = 50) List<UUID> tagIds,
            @Size(max = 50) List<UUID> mediaIds) {
        DiaryService.Command command() {
            return new DiaryService.Command(
                    clientId,
                    title,
                    diaryDate,
                    contentHtml,
                    mood,
                    visibility,
                    locked,
                    tagIds == null ? List.of() : tagIds,
                    mediaIds == null ? List.of() : mediaIds);
        }
    }

    public record CursorResponse<T>(List<T> items, String nextCursor, Long totalElements) {}

    public record DiaryResponse(
            UUID id,
            UUID spaceId,
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String contentText,
            String mood,
            String visibility,
            boolean locked,
            int version,
            String createdAt,
            String updatedAt,
            String deletedAt,
            List<DiaryTagResponse> tags,
            List<DiaryMediaResponse> media) {
        static DiaryResponse from(
                DiaryEntry diary,
                MediaRepresentationService media,
                long accountId,
                boolean elevated) {
            return new DiaryResponse(
                    diary.id(),
                    diary.spaceId(),
                    diary.title(),
                    diary.diaryDate(),
                    diary.contentHtml(),
                    diary.contentText(),
                    diary.mood(),
                    diary.visibility(),
                    diary.locked(),
                    diary.version(),
                    diary.createdAt() == null ? null : diary.createdAt().toString(),
                    diary.updatedAt() == null ? null : diary.updatedAt().toString(),
                    diary.deletedAt() == null ? null : diary.deletedAt().toString(),
                    diary.tags().stream().map(DiaryTagResponse::from).toList(),
                    diary.media().stream()
                            .map(
                                    value ->
                                            DiaryMediaResponse.from(
                                                    diary.spaceId(),
                                                    diary.id(),
                                                    value,
                                                    media,
                                                    accountId,
                                                    elevated))
                            .toList());
        }
    }

    public record DiaryTagResponse(UUID id, String name, String color) {
        static DiaryTagResponse from(DiaryEntry.TagRef tag) {
            return new DiaryTagResponse(tag.id(), tag.name(), tag.color());
        }
    }

    public record DiaryMediaResponse(
            UUID id,
            String mediaType,
            String caption,
            String takenAt,
            int position,
            String status,
            boolean protectedContent,
            MediaView.Representations representations) {
        static DiaryMediaResponse from(
                UUID spaceId,
                UUID diaryId,
                DiaryEntry.MediaRef value,
                MediaRepresentationService media,
                long accountId,
                boolean elevated) {
            MediaAccessContext context = MediaAccessContext.diary(accountId, diaryId, elevated);
            boolean reveal = !value.protectedContent() || elevated;
            return new DiaryMediaResponse(
                    value.id(),
                    value.mediaType(),
                    reveal ? value.caption() : null,
                    reveal && value.takenAt() != null ? value.takenAt().toString() : null,
                    value.position(),
                    value.status(),
                    value.protectedContent(),
                    media.links(
                            spaceId,
                            value.id(),
                            value.originalProfile(),
                            value.thumbnailProfile(),
                            value.previewProfile(),
                            context,
                            reveal));
        }
    }
}

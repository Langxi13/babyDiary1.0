package com.langxi.babydiary.v3.diary.api;

import com.langxi.babydiary.v3.diary.application.DiaryRepository;
import com.langxi.babydiary.v3.diary.application.DiaryService;
import com.langxi.babydiary.v3.diary.domain.DiaryEntry;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.platform.domain.CursorPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/diaries")
public class DiaryController {
    private final DiaryService diaries;
    private final MediaUrlSigner urls;

    public DiaryController(DiaryService diaries, MediaUrlSigner urls) {
        this.diaries = diaries;
        this.urls = urls;
    }

    @GetMapping
    public CursorResponse<DiaryResponse> list(@AuthenticationPrincipal V3Principal principal,
                                               @PathVariable UUID spaceId,
                                               @RequestParam(required = false) LocalDate startDate,
                                               @RequestParam(required = false) LocalDate endDate,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String mood,
                                               @RequestParam(required = false) UUID tagId,
                                               @RequestParam(defaultValue = "false") boolean trash,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String cursor) {
        CursorPage<DiaryEntry> page = diaries.list(spaceId, principal.accountId(),
                new DiaryService.ListQuery(startDate, endDate, keyword, mood, tagId, trash, cursor, size));
        return new CursorResponse<>(page.items().stream().map(diary -> DiaryResponse.from(diary, urls)).toList(),
                page.nextCursor(), page.totalElements());
    }

    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> detail(@AuthenticationPrincipal V3Principal principal,
                                                 @PathVariable UUID spaceId, @PathVariable UUID diaryId,
                                                 @RequestParam(defaultValue = "false") boolean trash) {
        DiaryEntry diary = diaries.detail(spaceId, diaryId, principal.accountId(), trash);
        return ResponseEntity.ok().eTag(etag(diary.version())).body(DiaryResponse.from(diary, urls));
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(@AuthenticationPrincipal V3Principal principal,
                                                 @PathVariable UUID spaceId,
                                                 @Valid @RequestBody DiaryRequest request) {
        DiaryEntry diary = diaries.create(spaceId, principal.accountId(), request.command());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(diary.version()))
                .header(HttpHeaders.LOCATION, "/api/v3/spaces/" + spaceId + "/diaries/" + diary.id())
                .body(DiaryResponse.from(diary, urls));
    }

    @PutMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> update(@AuthenticationPrincipal V3Principal principal,
                                                 @PathVariable UUID spaceId, @PathVariable UUID diaryId,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                 @Valid @RequestBody DiaryRequest request) {
        int version = requiredVersion(ifMatch);
        DiaryEntry diary = diaries.update(spaceId, diaryId, principal.accountId(), version, request.command());
        return ResponseEntity.ok().eTag(etag(diary.version())).body(DiaryResponse.from(diary, urls));
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal V3Principal principal,
                                       @PathVariable UUID spaceId, @PathVariable UUID diaryId,
                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        diaries.moveToTrash(spaceId, diaryId, principal.accountId(), requiredVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{diaryId}/restore")
    public ResponseEntity<DiaryResponse> restore(@AuthenticationPrincipal V3Principal principal,
                                                  @PathVariable UUID spaceId, @PathVariable UUID diaryId,
                                                  @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        DiaryEntry diary = diaries.restore(spaceId, diaryId, principal.accountId(), requiredVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(diary.version())).body(DiaryResponse.from(diary, urls));
    }

    @GetMapping("/{diaryId}/revisions")
    public List<DiaryRepository.RevisionSummary> revisions(@AuthenticationPrincipal V3Principal principal,
                                                             @PathVariable UUID spaceId, @PathVariable UUID diaryId) {
        return diaries.revisions(spaceId, diaryId, principal.accountId());
    }

    @PostMapping("/{diaryId}/revisions/{revisionId}/restore")
    public ResponseEntity<DiaryResponse> restoreRevision(@AuthenticationPrincipal V3Principal principal,
                                                          @PathVariable UUID spaceId, @PathVariable UUID diaryId,
                                                          @PathVariable long revisionId,
                                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        DiaryEntry diary = diaries.restoreRevision(spaceId, diaryId, revisionId, principal.accountId(), requiredVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(diary.version())).body(DiaryResponse.from(diary, urls));
    }

    private int requiredVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new V3Exception(HttpStatus.PRECONDITION_REQUIRED, "VERSION_REQUIRED", "编辑日记需要提供版本前置条件");
        }
        try {
            return Integer.parseInt(ifMatch.trim().replace("W/", "").replace("\"", ""));
        } catch (NumberFormatException exception) {
            throw new V3Exception(HttpStatus.BAD_REQUEST, "VERSION_INVALID", "版本前置条件无效");
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
            @Size(max = 50) List<UUID> mediaIds
    ) {
        DiaryService.Command command() {
            return new DiaryService.Command(clientId, title, diaryDate, contentHtml, mood,
                    visibility, locked, tagIds == null ? List.of() : tagIds,
                    mediaIds == null ? List.of() : mediaIds);
        }
    }

    public record CursorResponse<T>(List<T> items, String nextCursor, long totalElements) {
    }

    public record DiaryResponse(UUID id, UUID spaceId, String title, LocalDate diaryDate, String contentHtml,
                                String contentText, String mood, String visibility, boolean locked, int version,
                                String createdAt, String updatedAt, String deletedAt,
                                List<DiaryEntry.TagRef> tags, List<DiaryMediaResponse> media) {
        static DiaryResponse from(DiaryEntry diary, MediaUrlSigner urls) {
            return new DiaryResponse(diary.id(), diary.spaceId(), diary.title(), diary.diaryDate(), diary.contentHtml(),
                    diary.contentText(), diary.mood(), diary.visibility(), diary.locked(), diary.version(),
                    diary.createdAt() == null ? null : diary.createdAt().toString(),
                    diary.updatedAt() == null ? null : diary.updatedAt().toString(),
                    diary.deletedAt() == null ? null : diary.deletedAt().toString(), diary.tags(), diary.media().stream()
                    .map(value -> DiaryMediaResponse.from(diary.spaceId(), value, urls)).toList());
        }
    }

    public record DiaryMediaResponse(UUID id, String mediaType, String caption, String takenAt, int position,
                                     String status, String contentUrl, String thumbnailUrl) {
        static DiaryMediaResponse from(UUID spaceId, DiaryEntry.MediaRef media, MediaUrlSigner urls) {
            String original = media.originalProfile() == null ? null
                    : urls.url(spaceId, media.id(), "ORIGINAL", media.originalProfile());
            String thumbnail = media.thumbnailProfile() == null ? original
                    : urls.url(spaceId, media.id(), "THUMBNAIL", media.thumbnailProfile());
            return new DiaryMediaResponse(media.id(), media.mediaType(), media.caption(),
                    media.takenAt() == null ? null : media.takenAt().toString(), media.position(), media.status(),
                    original, thumbnail);
        }
    }
}

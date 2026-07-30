package com.langxi.babydiary.draft.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.langxi.babydiary.draft.application.DraftService;
import com.langxi.babydiary.draft.domain.DiaryDraft;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/drafts")
public class DraftController {
    private final DraftService drafts;

    public DraftController(DraftService drafts) {
        this.drafts = drafts;
    }

    @GetMapping
    public List<DiaryDraft> list(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId) {
        return drafts.list(spaceId, principal.accountId());
    }

    @GetMapping("/{draftKey}")
    public DiaryDraft detail(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId,
                             @PathVariable String draftKey) {
        return drafts.detail(spaceId, draftKey, principal.accountId());
    }

    @PutMapping("/{draftKey}")
    public DiaryDraft save(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId,
                           @PathVariable String draftKey, @Valid @RequestBody DraftRequest request) {
        return drafts.save(spaceId, draftKey, principal.accountId(), request.diaryId(), request.payload());
    }

    @DeleteMapping("/{draftKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId,
                       @PathVariable String draftKey) {
        drafts.delete(spaceId, draftKey, principal.accountId());
    }

    public record DraftRequest(UUID diaryId, @NotNull JsonNode payload) {
    }
}

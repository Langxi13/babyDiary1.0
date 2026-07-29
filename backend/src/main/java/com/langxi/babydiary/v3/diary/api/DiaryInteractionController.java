package com.langxi.babydiary.v3.diary.api;

import com.langxi.babydiary.v3.diary.application.DiaryInteractionService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/diaries/{diaryId}")
public class DiaryInteractionController {
    private final DiaryInteractionService interactions;
    public DiaryInteractionController(DiaryInteractionService interactions) { this.interactions = interactions; }

    @GetMapping("/comments")
    public List<DiaryInteractionService.Comment> comments(@AuthenticationPrincipal V3Principal principal,
                                                           @PathVariable UUID spaceId, @PathVariable UUID diaryId) {
        return interactions.comments(spaceId, diaryId, principal.accountId());
    }

    @PostMapping("/comments") @ResponseStatus(HttpStatus.CREATED)
    public DiaryInteractionService.Comment addComment(@AuthenticationPrincipal V3Principal principal,
                                                       @PathVariable UUID spaceId, @PathVariable UUID diaryId,
                                                       @Valid @RequestBody CommentRequest request) {
        return interactions.addComment(spaceId, diaryId, principal.accountId(), request.content());
    }

    @PutMapping("/comments/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateComment(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                              @PathVariable UUID diaryId, @PathVariable UUID commentId,
                              @Valid @RequestBody CommentRequest request) {
        interactions.updateComment(spaceId, diaryId, commentId, principal.accountId(), request.content());
    }

    @DeleteMapping("/comments/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                              @PathVariable UUID diaryId, @PathVariable UUID commentId) {
        interactions.deleteComment(spaceId, diaryId, commentId, principal.accountId());
    }

    @GetMapping("/reactions")
    public List<DiaryInteractionService.Reaction> reactions(@AuthenticationPrincipal V3Principal principal,
                                                             @PathVariable UUID spaceId, @PathVariable UUID diaryId) {
        return interactions.reactions(spaceId, diaryId, principal.accountId());
    }

    @PutMapping("/reactions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setReaction(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                            @PathVariable UUID diaryId, @Valid @RequestBody ReactionRequest request) {
        interactions.setReaction(spaceId, diaryId, principal.accountId(), request.emoji(), request.active());
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String content) {}
    public record ReactionRequest(@NotBlank @Size(max = 16) String emoji, boolean active) {}
}

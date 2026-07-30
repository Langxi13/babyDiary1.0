package com.langxi.babydiary.v3.ai.api;

import com.langxi.babydiary.v3.ai.application.AiAlbumProposalService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaAccessContext;
import com.langxi.babydiary.v3.media.application.MediaRepresentationService;
import com.langxi.babydiary.v3.media.application.MediaView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/ai-album-proposals")
public class AiAlbumProposalController {
    private final AiAlbumProposalService proposals;
    private final MediaRepresentationService media;

    public AiAlbumProposalController(AiAlbumProposalService proposals, MediaRepresentationService media) {
        this.proposals = proposals;
        this.media = media;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalResponse generate(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                     @Valid @RequestBody GenerateRequest request) {
        return response(principal.accountId(), proposals.generate(spaceId, principal.accountId(), request.startDate(),
                request.endDate(), request.prompt()));
    }

    @GetMapping("/{proposalId}")
    public ProposalResponse detail(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                   @PathVariable UUID proposalId) {
        return response(principal.accountId(), proposals.detail(spaceId, principal.accountId(), proposalId));
    }

    @PutMapping("/{proposalId}")
    public ProposalResponse update(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                   @PathVariable UUID proposalId, @Valid @RequestBody UpdateRequest request) {
        return response(principal.accountId(), proposals.update(spaceId, principal.accountId(), proposalId,
                request.albums().stream().map(CandidateRequest::command).toList()));
    }

    @PostMapping("/{proposalId}/confirm")
    public ProposalResponse confirm(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                                    @PathVariable UUID proposalId) {
        return response(principal.accountId(), proposals.confirm(spaceId, principal.accountId(), proposalId));
    }

    @DeleteMapping("/{proposalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                        @PathVariable UUID proposalId) {
        proposals.dismiss(spaceId, principal.accountId(), proposalId);
    }

    private ProposalResponse response(long accountId, AiAlbumProposalService.Proposal proposal) {
        return new ProposalResponse(proposal.proposalId(), proposal.status(), proposal.startDate(), proposal.endDate(),
                proposal.prompt(), proposal.model(), proposal.albums().stream().map(candidate -> new CandidateResponse(
                candidate.mode(), candidate.targetAlbumId(), candidate.targetAlbumName(), candidate.title(),
                candidate.description(), candidate.diaryIds(), candidate.assetIds(), candidate.photos().stream()
                .map(item -> new PhotoResponse(item.id(), media.view(item, MediaAccessContext.direct(accountId,false))))
                .toList(), candidate.discarded())).toList(), proposal.createdAt(), proposal.updatedAt());
    }

    public record GenerateRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                  @Size(max = 1000) String prompt) {}
    public record UpdateRequest(@NotNull @Size(min = 1, max = 50) List<@Valid CandidateRequest> albums) {}
    public record CandidateRequest(String mode, UUID targetAlbumId, @Size(max = 100) String title,
                                   @Size(max = 500) String description, @Size(max = 500) List<UUID> diaryIds,
                                   @Size(max = 500) List<UUID> assetIds, boolean discarded) {
        AiAlbumProposalService.CandidateCommand command() {
            return new AiAlbumProposalService.CandidateCommand(mode, targetAlbumId, title, description,
                    diaryIds == null ? List.of() : diaryIds, assetIds == null ? List.of() : assetIds, discarded);
        }
    }
    public record ProposalResponse(UUID proposalId, String status, LocalDate startDate, LocalDate endDate,
                                   String prompt, String model, List<CandidateResponse> albums,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record CandidateResponse(String mode, UUID targetAlbumId, String targetAlbumName, String title,
                                    String description, List<UUID> diaryIds, List<UUID> assetIds,
                                    List<PhotoResponse> photos, boolean discarded) {}
    public record PhotoResponse(UUID assetId, MediaView media) {}
}

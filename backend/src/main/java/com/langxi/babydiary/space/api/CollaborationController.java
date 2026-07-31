package com.langxi.babydiary.space.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.space.application.CollaborationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3")
public class CollaborationController {
    private final CollaborationService collaboration;

    public CollaborationController(CollaborationService collaboration) {
        this.collaboration = collaboration;
    }

    @GetMapping("/spaces/{spaceId}/members")
    public List<CollaborationService.Member> members(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId) {
        return collaboration.members(spaceId, principal.accountId());
    }

    @PostMapping("/spaces/{spaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public CollaborationService.InvitationCreated invite(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody InviteRequest request) {
        return collaboration.invite(
                spaceId, principal.accountId(), request.email(), request.role());
    }

    @PostMapping("/invitations/{token}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable String token) {
        collaboration.accept(token, principal.accountId());
    }

    @PutMapping("/spaces/{spaceId}/members/{accountId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRole(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID accountId,
            @Valid @RequestBody RoleRequest request) {
        collaboration.updateRole(spaceId, principal.accountId(), accountId, request.role());
    }

    @DeleteMapping("/spaces/{spaceId}/members/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID accountId) {
        collaboration.removeMember(spaceId, principal.accountId(), accountId);
    }

    public record InviteRequest(
            @NotBlank @Email String email, @Pattern(regexp = "MEMBER|VIEWER") String role) {}

    public record RoleRequest(
            @NotBlank @Pattern(regexp = "OWNER|ADMIN|MEMBER|VIEWER") String role) {}
}

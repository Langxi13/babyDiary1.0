package com.langxi.babydiary.sync.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.sync.application.SyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/sync")
public class SyncController {
    private final SyncService sync;
    private final StepUpService stepUp;

    public SyncController(SyncService sync, StepUpService stepUp) {
        this.sync = sync;
        this.stepUp = stepUp;
    }

    @GetMapping("/pull")
    public SyncService.PullResponse pull(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "200") int limit) {
        return sync.pull(spaceId, principal.accountId(), cursor, limit);
    }

    @PostMapping("/push")
    public List<SyncService.PushResult> push(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody PushRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        return sync.push(
                spaceId,
                principal.accountId(),
                request.operations().stream().map(OperationRequest::command).toList(),
                stepUp.valid(principal, token));
    }

    public record PushRequest(
            @NotEmpty @Size(max = 100) List<@Valid OperationRequest> operations) {}

    public record OperationRequest(
            @NotNull UUID operationId,
            @NotNull @Pattern(regexp = "DIARY") String entityType,
            @NotNull @Pattern(regexp = "CREATE|UPDATE|DELETE|RESTORE") String action,
            UUID entityId,
            Integer baseVersion,
            JsonNode payload) {
        SyncService.PushOperation command() {
            return new SyncService.PushOperation(
                    operationId, entityType, action, entityId, baseVersion, payload);
        }
    }
}

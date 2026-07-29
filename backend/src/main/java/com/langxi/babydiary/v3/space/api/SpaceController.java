package com.langxi.babydiary.v3.space.api;

import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.space.application.SpaceService;
import com.langxi.babydiary.v3.space.domain.SpaceSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces")
public class SpaceController {
    private final SpaceService spaces;

    public SpaceController(SpaceService spaces) {
        this.spaces = spaces;
    }

    @GetMapping
    public List<SpaceSummary> list(@AuthenticationPrincipal V3Principal principal) {
        return spaces.list(principal.accountId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceSummary create(@AuthenticationPrincipal V3Principal principal,
                               @Valid @RequestBody CreateSpaceRequest request) {
        return spaces.create(principal.accountId(), request.name(), request.defaultVisibility());
    }

    @PutMapping("/{spaceId}")
    public SpaceSummary update(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                               @Valid @RequestBody UpdateSpaceRequest request) {
        return spaces.update(spaceId, principal.accountId(), request.name(), request.defaultVisibility());
    }

    public record CreateSpaceRequest(
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "PRIVATE|SHARED") String defaultVisibility
    ) {
    }

    public record UpdateSpaceRequest(@NotBlank @Size(max = 100) String name,
                                     @Pattern(regexp = "PRIVATE|SHARED") String defaultVisibility) {
    }
}

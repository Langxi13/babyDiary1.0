package com.langxi.babydiary.v3.anniversary.api;

import com.langxi.babydiary.v3.anniversary.application.AnniversaryService;
import com.langxi.babydiary.v3.anniversary.domain.Anniversary;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/anniversaries")
public class AnniversaryController {
    private final AnniversaryService anniversaries;

    public AnniversaryController(AnniversaryService anniversaries) {
        this.anniversaries = anniversaries;
    }

    @GetMapping
    public List<Anniversary> list(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId) {
        return anniversaries.list(spaceId, principal.accountId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Anniversary create(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                              @Valid @RequestBody AnniversaryRequest request) {
        return anniversaries.create(spaceId, principal.accountId(), request.command());
    }

    @PutMapping("/{anniversaryId}")
    public Anniversary update(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                              @PathVariable UUID anniversaryId, @Valid @RequestBody AnniversaryRequest request) {
        return anniversaries.update(spaceId, anniversaryId, principal.accountId(), request.command());
    }

    @DeleteMapping("/{anniversaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                       @PathVariable UUID anniversaryId) {
        anniversaries.delete(spaceId, anniversaryId, principal.accountId());
    }

    public record AnniversaryRequest(@NotBlank @Size(max = 100) String title, @NotNull LocalDate date,
                                     @Size(max = 5000) String description, UUID coverAssetId, int sortOrder) {
        AnniversaryService.Command command() {
            return new AnniversaryService.Command(title, date, description, coverAssetId, sortOrder);
        }
    }
}

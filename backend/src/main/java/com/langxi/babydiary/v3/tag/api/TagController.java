package com.langxi.babydiary.v3.tag.api;

import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.tag.application.TagService;
import com.langxi.babydiary.v3.tag.domain.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/tags")
public class TagController {
    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    public List<TagResponse> list(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId) {
        return tags.list(spaceId, principal.accountId()).stream().map(TagResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID spaceId,
                              @Valid @RequestBody TagRequest request) {
        return TagResponse.from(tags.create(spaceId, principal.accountId(), request.name(), request.color()));
    }

    public record TagRequest(@Size(max = 32) String name,
                             @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$") String color) {
    }

    public record TagResponse(UUID id, UUID spaceId, String name, String color) {
        static TagResponse from(Tag tag) {
            return new TagResponse(tag.id(), tag.spaceId(), tag.name(), tag.color());
        }
    }
}

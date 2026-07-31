package com.langxi.babydiary.identity.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.ProfileService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaLinkView;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.platform.api.ApiContract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping((ApiContract.ROOT + "/account"))
public class ProfileController {
    private final ProfileService profiles;
    private final MediaRepresentationService media;

    public ProfileController(ProfileService profiles, MediaRepresentationService media) {
        this.profiles = profiles;
        this.media = media;
    }

    @GetMapping("/profile")
    public ProfileResponse profile(@AuthenticationPrincipal AccountPrincipal principal) {
        return ProfileResponse.from(
                profiles.profile(principal.accountId()), media, principal.accountId());
    }

    @PutMapping("/profile")
    public ProfileResponse update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(
                profiles.update(
                        principal.accountId(),
                        request.username(),
                        request.email(),
                        request.timezone()),
                media,
                principal.accountId());
    }

    @PutMapping("/avatar")
    public ProfileResponse avatar(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AvatarRequest request) {
        return ProfileResponse.from(
                profiles.setAvatar(principal.accountId(), request.spaceId(), request.assetId()),
                media,
                principal.accountId());
    }

    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAvatar(@AuthenticationPrincipal AccountPrincipal principal) {
        profiles.clearAvatar(principal.accountId());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PasswordRequest request) {
        profiles.changePassword(
                principal.accountId(), request.currentPassword(), request.newPassword());
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 100) String username,
            @Size(max = 255) String email,
            @Size(max = 64) String timezone) {}

    public record AvatarRequest(@NotNull UUID spaceId, @NotNull UUID assetId) {}

    public record PasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(min = 8, max = 200) String newPassword) {}

    public record ProfileResponse(
            UUID id,
            String username,
            String email,
            boolean emailVerified,
            String role,
            String timezone,
            java.time.LocalDateTime createdAt,
            MediaLinkView avatarMedia) {
        static ProfileResponse from(
                ProfileService.ProfileView profile,
                MediaRepresentationService media,
                long accountId) {
            return new ProfileResponse(
                    profile.id(),
                    profile.username(),
                    profile.email(),
                    profile.emailVerified(),
                    profile.role(),
                    profile.timezone(),
                    profile.createdAt(),
                    profile.avatarAssetId() == null
                                    || profile.avatarVariantType() == null
                                    || profile.avatarVariantProfile() == null
                            ? null
                            : media.link(
                                    profile.avatarSpaceId(),
                                    profile.avatarAssetId(),
                                    profile.avatarVariantType(),
                                    profile.avatarVariantProfile(),
                                    MediaAccessContext.avatar(accountId, profile.id(), false)));
        }
    }
}

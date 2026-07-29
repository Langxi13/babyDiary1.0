package com.langxi.babydiary.v3.identity.api;

import com.langxi.babydiary.v3.identity.application.ProfileRepository;
import com.langxi.babydiary.v3.identity.application.ProfileService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v3/account")
public class ProfileController {
    private final ProfileService profiles;
    private final MediaUrlSigner mediaUrls;

    public ProfileController(ProfileService profiles, MediaUrlSigner mediaUrls) {
        this.profiles = profiles;
        this.mediaUrls = mediaUrls;
    }

    @GetMapping("/profile")
    public ProfileResponse profile(@AuthenticationPrincipal V3Principal principal) {
        return ProfileResponse.from(profiles.profile(principal.accountId()), mediaUrls);
    }

    @PutMapping("/profile")
    public ProfileResponse update(@AuthenticationPrincipal V3Principal principal,
                                  @Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(profiles.update(principal.accountId(), request.username(), request.email(), request.timezone()), mediaUrls);
    }

    @PutMapping("/avatar")
    public ProfileResponse avatar(@AuthenticationPrincipal V3Principal principal,
                                  @Valid @RequestBody AvatarRequest request) {
        return ProfileResponse.from(profiles.setAvatar(principal.accountId(), request.spaceId(), request.assetId()), mediaUrls);
    }

    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAvatar(@AuthenticationPrincipal V3Principal principal) {
        profiles.clearAvatar(principal.accountId());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal V3Principal principal, @Valid @RequestBody PasswordRequest request) {
        profiles.changePassword(principal.accountId(), request.currentPassword(), request.newPassword());
    }

    public record ProfileRequest(@NotBlank @Size(max = 100) String username,
                                 @Size(max = 255) String email, @Size(max = 64) String timezone) {
    }

    public record AvatarRequest(@NotNull UUID spaceId, @NotNull UUID assetId) {
    }

    public record PasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                  @NotBlank @Size(min = 8, max = 200) String newPassword) {
    }

    public record ProfileResponse(UUID id, String username, String email, boolean emailVerified, String role,
                                  String timezone, UUID avatarAssetId, UUID avatarSpaceId, AvatarMedia avatarMedia) {
        static ProfileResponse from(ProfileRepository.Profile profile, MediaUrlSigner mediaUrls) {
            return new ProfileResponse(profile.id(), profile.username(), profile.email(), profile.emailVerified(),
                    profile.role(), profile.timezone(), profile.avatarAssetId(), profile.avatarSpaceId(),
                    profile.avatarAssetId() == null ? null : new AvatarMedia(profile.avatarAssetId(),
                    mediaUrls.url(profile.avatarSpaceId(), profile.avatarAssetId(), "ORIGINAL")));
        }
    }

    public record AvatarMedia(UUID assetId, String contentUrl) {
    }
}

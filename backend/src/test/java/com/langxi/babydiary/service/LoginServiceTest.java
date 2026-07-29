package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.MediaAssetVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.MediaAsset;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    private static final String AVATAR_ID = "11111111-1111-1111-1111-111111111111";
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SpaceService spaceService;
    @Mock private AccountSecurityMapper accountSecurityMapper;
    @Mock private InvitationCodeService invitationCodeService;
    @Mock private MediaService mediaService;
    @InjectMocks private LoginService service;

    @BeforeEach
    void setUp() {
        service.setPasswordEncoder(passwordEncoder);
    }

    @Test
    void registerReportsDuplicateUsername() {
        when(userMapper.findByUsername("same")).thenReturn(new User());
        assertThatThrownBy(() -> service.registerUser("same", "password", "invite"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS.getCode()));
    }

    @Test
    void registerTrimsUsernameAndCreatesPersonalSpace() {
        when(invitationCodeService.matches("invite")).thenReturn(true);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        doAnswer(invocation -> { ((User) invocation.getArgument(0)).setUserId(42); return null; })
                .when(userMapper).insertUser(any(User.class));
        when(userMapper.countUsers()).thenReturn(2);

        service.registerUser(" new-user ", "password", "invite");

        verify(userMapper).insertUser(org.mockito.ArgumentMatchers.argThat(user ->
                "new-user".equals(user.getUsername()) && "encoded".equals(user.getPassword())));
        verify(spaceService).ensurePersonalSpace(42, "new-user");
    }

    @Test
    void changePasswordInvalidatesSessions() {
        User user = new User();
        user.setUserId(8);
        user.setPassword("encoded-old");
        when(userMapper.findById(8)).thenReturn(user);
        when(passwordEncoder.matches("oldpass", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("encoded-new");

        service.changePassword(8, "oldpass", "newpass1", "newpass1");

        verify(userMapper).updatePasswordAndIncrementTokenVersion(8, "encoded-new");
        verify(accountSecurityMapper).revokeAllSessions(8);
        verify(accountSecurityMapper).deleteAccountTokens(8, "STEP_UP");
    }

    @Test
    void updateAvatarStoresProfileAssetAndReturnsSignedMedia() throws Exception {
        User user = new User();
        user.setUserId(8);
        User updated = new User();
        updated.setUserId(8);
        updated.setAvatarAssetId(AVATAR_ID);
        when(userMapper.findById(8)).thenReturn(user, updated);
        DiarySpace space = new DiarySpace();
        space.setPublicId("space-one");
        when(spaceService.requirePersonalSpace(8)).thenReturn(space);
        MockMultipartFile file = new MockMultipartFile("avatarFile", "avatar.png", "image/png", new byte[]{1});
        MediaAssetVO uploaded = new MediaAssetVO();
        uploaded.setAssetId(AVATAR_ID);
        MediaAsset asset = new MediaAsset();
        asset.setAssetId(9L);
        asset.setPublicId(AVATAR_ID);
        when(mediaService.upload("space-one", 8, file, null, "用户头像", null, null, null, null, null))
                .thenReturn(uploaded);
        when(mediaService.requireOwnedAsset("space-one", AVATAR_ID, 8)).thenReturn(asset);
        when(mediaService.findByPublicId(AVATAR_ID)).thenReturn(asset);
        when(mediaService.toVO(asset)).thenReturn(uploaded);

        User result = service.updateAvatar(8, file);

        verify(mediaService).updateUsage("space-one", AVATAR_ID, 8, "PROFILE", false);
        verify(userMapper).updateAvatarAsset(8, 9L);
        assertThat(result.getAvatarMedia()).isSameAs(uploaded);
    }
}

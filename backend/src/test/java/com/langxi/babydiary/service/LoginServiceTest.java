package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private ImageStorageService imageStorageService = new ImageStorageService();

    @InjectMocks
    private LoginService loginService;

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setUp() {
        loginService.setPasswordEncoder(passwordEncoder);
        ReflectionTestUtils.setField(loginService, "privateInvitationCode", "invite");
        ReflectionTestUtils.setField(imageStorageService, "uploadDir", uploadDir.toString());
    }

    @Test
    void registerUserReportsDuplicateUsernameAsBusinessError() {
        when(userMapper.findByUsername("same")).thenReturn(new User());

        assertThatThrownBy(() -> loginService.registerUser("same", "password", "invite"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS.getCode()));
    }

    @Test
    void registerUserReportsInvalidInvitationCodeAsBusinessError() {
        when(userMapper.findByUsername("new-user")).thenReturn(null);

        assertThatThrownBy(() -> loginService.registerUser("new-user", "password", "wrong"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_INVITATION_CODE.getCode()));
    }

    @Test
    void registerUserTrimsUsernameBeforeSaving() {
        when(userMapper.findByUsername("new-user")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("encoded");

        loginService.registerUser(" new-user ", "password", "invite");

        verify(userMapper).insertUser(org.mockito.ArgumentMatchers.argThat(user ->
                "new-user".equals(user.getUsername()) && "encoded".equals(user.getPassword())));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = new User();
        user.setUserId(8);
        user.setPassword("encoded-old");
        when(userMapper.findById(8)).thenReturn(user);
        when(passwordEncoder.matches("bad-old", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> loginService.changePassword(8, "bad-old", "newpass1", "newpass1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.LOGIN_FAILED.getCode()));
    }

    @Test
    void changePasswordEncodesPasswordAndInvalidatesTokens() {
        User user = new User();
        user.setUserId(8);
        user.setPassword("encoded-old");
        when(userMapper.findById(8)).thenReturn(user);
        when(passwordEncoder.matches("oldpass", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("encoded-new");

        loginService.changePassword(8, "oldpass", "newpass1", "newpass1");

        verify(userMapper).updatePasswordAndIncrementTokenVersion(8, "encoded-new");
    }

    @Test
    void changePasswordRejectsMismatchedConfirmation() {
        assertThatThrownBy(() -> loginService.changePassword(8, "oldpass", "newpass1", "newpass2"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH.getCode()));
    }

    @Test
    void updateAvatarStoresImageAndReturnsFreshUser() throws Exception {
        User existing = new User();
        existing.setUserId(8);
        existing.setAvatarPath("avatar_8_old.jpg");
        when(userMapper.findById(8)).thenReturn(existing);

        User updated = new User();
        updated.setUserId(8);
        updated.setUsername("test-user");
        updated.setAvatarPath("avatar_8_123.jpg");
        when(userMapper.findById(8)).thenReturn(existing, updated);

        Files.write(uploadDir.resolve("avatar_8_old.jpg"), new byte[]{9});
        MockMultipartFile avatar = new MockMultipartFile(
                "avatarFile",
                "avatar.jpg",
                "image/jpeg",
                imageBytes()
        );

        User result = loginService.updateAvatar(8, avatar);

        verify(userMapper).updateAvatarPath(org.mockito.ArgumentMatchers.eq(8), org.mockito.ArgumentMatchers.contains("avatar_8_"));
        assertThat(result.getAvatarPath()).isEqualTo("avatar_8_123.jpg");
        assertThat(Files.exists(uploadDir.resolve("avatar_8_old.jpg"))).isFalse();
        assertThat(Files.list(uploadDir).anyMatch(path -> path.getFileName().toString().startsWith("avatar_8_"))).isTrue();
    }

    @Test
    void updateAvatarRejectsNonImageFile() {
        User existing = new User();
        existing.setUserId(8);
        when(userMapper.findById(8)).thenReturn(existing);

        MockMultipartFile avatar = new MockMultipartFile(
                "avatarFile",
                "avatar.txt",
                "text/plain",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> loginService.updateAvatar(8, avatar))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED.getCode()));
    }

    @Test
    void updateAvatarIgnoresUnownedLegacyAvatarPath() throws Exception {
        User existing = new User();
        existing.setUserId(8);
        existing.setAvatarPath("../old-avatar.jpg");

        User updated = new User();
        updated.setUserId(8);
        updated.setUsername("test-user");
        updated.setAvatarPath("avatar_8_456.jpg");
        when(userMapper.findById(8)).thenReturn(existing, updated);

        MockMultipartFile avatar = new MockMultipartFile(
                "avatarFile",
                "avatar.jpg",
                "image/jpeg",
                imageBytes()
        );

        User result = loginService.updateAvatar(8, avatar);

        verify(userMapper).updateAvatarPath(org.mockito.ArgumentMatchers.eq(8), org.mockito.ArgumentMatchers.contains("avatar_8_"));
        assertThat(result.getAvatarPath()).isEqualTo("avatar_8_456.jpg");
    }

    private byte[] imageBytes() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.PINK.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}

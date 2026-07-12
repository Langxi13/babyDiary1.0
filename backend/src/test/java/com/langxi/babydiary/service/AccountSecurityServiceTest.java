package com.langxi.babydiary.service;

import com.langxi.babydiary.entity.AccountToken;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {
    @Mock private AccountSecurityMapper securityMapper;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccountMailService mailService;
    @Mock private StepUpTokenVerifier stepUpTokenVerifier;

    @Test
    void passwordResetConsumesTheLockedTokenBeforeChangingTheAccount() {
        AccountSecurityService service = new AccountSecurityService(
                securityMapper, userMapper, passwordEncoder, mailService, stepUpTokenVerifier);
        AccountToken token = token(4L, 2);
        String rawToken = "one-time-token";
        when(securityMapper.findValidAccountTokenForUpdate(
                SecureTokens.sha256(rawToken), "RESET_PASSWORD")).thenReturn(token);
        when(securityMapper.consumeAccountToken(4L)).thenReturn(1);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        service.resetPassword(rawToken, "new-password");

        InOrder order = inOrder(securityMapper, userMapper);
        order.verify(securityMapper).findValidAccountTokenForUpdate(
                SecureTokens.sha256(rawToken), "RESET_PASSWORD");
        order.verify(securityMapper).consumeAccountToken(4L);
        order.verify(userMapper).updatePasswordAndIncrementTokenVersion(2, "encoded-password");
        verify(securityMapper).revokeAllSessions(2);
    }

    @Test
    void passwordResetStopsWhenTheOneTimeTokenCannotBeConsumed() {
        AccountSecurityService service = new AccountSecurityService(
                securityMapper, userMapper, passwordEncoder, mailService, stepUpTokenVerifier);
        AccountToken token = token(4L, 2);
        String rawToken = "one-time-token";
        when(securityMapper.findValidAccountTokenForUpdate(
                SecureTokens.sha256(rawToken), "RESET_PASSWORD")).thenReturn(token);
        when(securityMapper.consumeAccountToken(4L)).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword(rawToken, "new-password"))
                .isInstanceOf(BusinessException.class);

        verify(userMapper, never()).updatePasswordAndIncrementTokenVersion(2, "new-password");
        verify(passwordEncoder, never()).encode("new-password");
    }

    private AccountToken token(long tokenId, int userId) {
        AccountToken token = new AccountToken();
        token.setTokenId(tokenId);
        token.setUserId(userId);
        return token;
    }
}

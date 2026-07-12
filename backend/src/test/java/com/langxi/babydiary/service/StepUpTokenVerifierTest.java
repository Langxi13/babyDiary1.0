package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.AccountToken;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepUpTokenVerifierTest {
    @Mock
    private AccountSecurityMapper securityMapper;

    @Test
    void acceptsOnlyAValidTokenOwnedByTheCurrentUser() {
        StepUpTokenVerifier verifier = new StepUpTokenVerifier(securityMapper);
        AccountToken token = new AccountToken();
        token.setUserId(7);
        when(securityMapper.findValidAccountToken(SecureTokens.sha256("step-token"), "STEP_UP"))
                .thenReturn(token);

        assertThatCode(() -> verifier.require(7, "step-token")).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.require(8, "step-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.DIARY_LOCKED.getCode()));
    }

    @Test
    void missingTokenRequiresStepUpWithoutQueryingTheDatabase() {
        StepUpTokenVerifier verifier = new StepUpTokenVerifier(securityMapper);

        assertThatThrownBy(() -> verifier.require(7, ""))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.DIARY_LOCKED.getCode()));
    }
}

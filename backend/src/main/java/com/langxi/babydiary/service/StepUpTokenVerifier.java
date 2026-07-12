package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.AccountToken;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.stereotype.Service;

@Service
public class StepUpTokenVerifier {
    private final AccountSecurityMapper securityMapper;

    public StepUpTokenVerifier(AccountSecurityMapper securityMapper) {
        this.securityMapper = securityMapper;
    }

    public void require(Integer userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.DIARY_LOCKED);
        }
        AccountToken token = securityMapper.findValidAccountToken(SecureTokens.sha256(rawToken), "STEP_UP");
        if (token == null || !userId.equals(token.getUserId())) {
            throw new BusinessException(ErrorCode.DIARY_LOCKED);
        }
    }
}

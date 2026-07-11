package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {
    @Test
    void blocksRequestsBeyondTheConfiguredWindowLimit() {
        RateLimitService service = new RateLimitService();
        service.require("login", "user-one", 2, Duration.ofMinutes(1));
        service.require("login", "user-one", 2, Duration.ofMinutes(1));

        assertThatThrownBy(() -> service.require("login", "user-one", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.RATE_LIMITED.getCode()));
    }

    @Test
    void acceptsForwardedAddressOnlyFromLoopbackProxy() {
        RateLimitService service = new RateLimitService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.18, 127.0.0.1");

        assertThat(service.clientAddress(request)).isEqualTo("203.0.113.18");
    }
}

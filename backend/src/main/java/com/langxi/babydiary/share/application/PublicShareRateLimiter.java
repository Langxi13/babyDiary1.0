package com.langxi.babydiary.share.application;

import com.langxi.babydiary.platform.application.RequestRateLimiter;
import org.springframework.stereotype.Component;

@Component
public class PublicShareRateLimiter {
    private final RequestRateLimiter limiter;

    public PublicShareRateLimiter(RequestRateLimiter limiter) {
        this.limiter = limiter;
    }

    public void require(String key) {
        limiter.require("public-share", key, 20, 900);
    }
}

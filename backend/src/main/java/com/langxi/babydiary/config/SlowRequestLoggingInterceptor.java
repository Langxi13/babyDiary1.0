package com.langxi.babydiary.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class SlowRequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_NANO_TIME_ATTRIBUTE = SlowRequestLoggingInterceptor.class.getName() + ".startNanoTime";

    @Value("${app.http.slow-request-threshold-ms:1000}")
    private long slowRequestThresholdMs;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_NANO_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object value = request.getAttribute(START_NANO_TIME_ATTRIBUTE);
        if (!(value instanceof Long)) {
            return;
        }
        long elapsedMs = (System.nanoTime() - (Long) value) / 1_000_000L;
        if (elapsedMs < slowRequestThresholdMs) {
            return;
        }
        log.warn("慢接口: method={}, uri={}, status={}, elapsedMs={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                elapsedMs);
    }
}

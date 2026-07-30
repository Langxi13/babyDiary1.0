package com.langxi.babydiary.platform.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SlowRequestLoggingInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SlowRequestLoggingInterceptor.class);
    private static final String START_TIME =
            SlowRequestLoggingInterceptor.class.getName() + ".startTime";

    private final long thresholdMillis;

    public SlowRequestLoggingInterceptor(
            @Value("${app.http.slow-request-threshold-ms:1000}") long thresholdMillis) {
        this.thresholdMillis = Math.max(1, thresholdMillis);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        Object started = request.getAttribute(START_TIME);
        if (!(started instanceof Long startedNanos)) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (elapsedMillis >= thresholdMillis) {
            log.warn(
                    "Slow request method={} path={} status={} elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis);
        }
    }
}

package com.langxi.babydiary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AndroidVersionGateFilter extends OncePerRequestFilter {
    private final ClientReleaseProperties releaseProperties;
    private final ObjectMapper objectMapper;

    public AndroidVersionGateFilter(ClientReleaseProperties releaseProperties, ObjectMapper objectMapper) {
        this.releaseProperties = releaseProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/")
                || path.equals("/api/v2/client/bootstrap")
                || path.startsWith("/api/v2/media/public/")
                || !"android".equalsIgnoreCase(request.getHeader("X-Client-Platform"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ClientReleaseProperties.Android release = releaseProperties.getAndroid();
        int build = parseBuild(request.getHeader("X-Client-Version-Code"));
        if (!release.isEnabled() || build >= release.getMinimumVersionCode()) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(426);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), Result.error(ErrorCode.CLIENT_UPGRADE_REQUIRED));
    }

    private int parseBuild(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}

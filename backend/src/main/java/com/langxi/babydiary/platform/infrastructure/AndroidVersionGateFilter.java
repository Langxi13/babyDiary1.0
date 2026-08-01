package com.langxi.babydiary.platform.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.api.ApiContract;
import com.langxi.babydiary.platform.api.ClientRequestHeaders;
import com.langxi.babydiary.platform.application.ClientReleaseProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AndroidVersionGateFilter extends OncePerRequestFilter {
    static final String PLATFORM_HEADER = ClientRequestHeaders.PLATFORM;
    static final String VERSION_CODE_HEADER = ClientRequestHeaders.VERSION_CODE;

    private final ClientReleaseProperties releases;
    private final ObjectMapper objectMapper;

    public AndroidVersionGateFilter(ClientReleaseProperties releases, ObjectMapper objectMapper) {
        this.releases = releases;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !path.startsWith(ApiContract.ROOT + "/")
                || path.equals(ApiContract.ROOT + "/client/bootstrap")
                || path.startsWith(ApiContract.ROOT + "/public/")
                || !"android".equalsIgnoreCase(request.getHeader(PLATFORM_HEADER));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ClientReleaseProperties.Android android = releases.getAndroid();
        if (!android.isUsable()
                || parseVersionCode(request.getHeader(VERSION_CODE_HEADER))
                        >= android.getMinimumVersionCode()) {
            chain.doFilter(request, response);
            return;
        }

        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UPGRADE_REQUIRED, "当前安卓版本过低，请升级后继续使用");
        detail.setTitle("Client upgrade required");
        detail.setType(URI.create("urn:baby-diary:problem:client-upgrade-required"));
        detail.setProperty("code", "CLIENT_UPGRADE_REQUIRED");
        detail.setProperty("minimumVersionCode", android.getMinimumVersionCode());
        detail.setProperty("latestVersionCode", android.getLatestVersionCode());
        detail.setProperty("downloadUrl", android.getDownloadUrl());
        detail.setProperty("mandatory", true);

        response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), detail);
    }

    private int parseVersionCode(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}

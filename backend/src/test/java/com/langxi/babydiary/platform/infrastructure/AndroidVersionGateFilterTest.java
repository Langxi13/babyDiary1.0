package com.langxi.babydiary.platform.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.ClientReleaseProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidVersionGateFilterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AndroidVersionGateFilter filter;

    @BeforeEach
    void setUp() {
        ClientReleaseProperties releases = new ClientReleaseProperties();
        ClientReleaseProperties.Android android = releases.getAndroid();
        android.setEnabled(true);
        android.setMinimumVersionCode(10);
        android.setLatestVersionCode(12);
        android.setLatestVersionName("3.1.0");
        android.setDownloadUrl("/downloads/baby-diary.apk");
        filter = new AndroidVersionGateFilter(releases, objectMapper);
    }

    @Test
    void rejectsOutdatedAndroidBeforeAuthentication() throws Exception {
        MockHttpServletRequest request = request("/api/v3/spaces", "android", "3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, chain(continued));

        assertThat(continued).isFalse();
        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.findValue("code").asText()).isEqualTo("CLIENT_UPGRADE_REQUIRED");
        assertThat(body.findValue("minimumVersionCode").asInt()).isEqualTo(10);
        assertThat(body.findValue("downloadUrl").asText()).isEqualTo("/downloads/baby-diary.apk");
    }

    @Test
    void allowsSupportedAndroidAndWebRequests() throws Exception {
        AtomicBoolean supportedContinued = new AtomicBoolean();
        filter.doFilter(request("/api/v3/spaces", "android", "10"), new MockHttpServletResponse(),
                chain(supportedContinued));
        assertThat(supportedContinued).isTrue();

        AtomicBoolean webContinued = new AtomicBoolean();
        filter.doFilter(request("/api/v3/spaces", null, null), new MockHttpServletResponse(), chain(webContinued));
        assertThat(webContinued).isTrue();
    }

    @Test
    void neverBlocksBootstrapOrPublicMedia() throws Exception {
        AtomicBoolean bootstrapContinued = new AtomicBoolean();
        filter.doFilter(request("/api/v3/client/bootstrap", "android", "1"), new MockHttpServletResponse(),
                chain(bootstrapContinued));
        assertThat(bootstrapContinued).isTrue();

        AtomicBoolean publicContinued = new AtomicBoolean();
        filter.doFilter(request("/api/v3/public/media/asset/content", "android", "1"),
                new MockHttpServletResponse(), chain(publicContinued));
        assertThat(publicContinued).isTrue();
    }

    private MockHttpServletRequest request(String path, String platform, String versionCode) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (platform != null) request.addHeader(AndroidVersionGateFilter.PLATFORM_HEADER, platform);
        if (versionCode != null) request.addHeader(AndroidVersionGateFilter.VERSION_CODE_HEADER, versionCode);
        return request;
    }

    private FilterChain chain(AtomicBoolean continued) {
        return (request, response) -> continued.set(true);
    }
}

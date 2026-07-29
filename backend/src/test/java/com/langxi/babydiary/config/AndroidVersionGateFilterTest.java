package com.langxi.babydiary.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidVersionGateFilterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AndroidVersionGateFilter filter;

    @BeforeEach
    void setUp() {
        ClientReleaseProperties properties = new ClientReleaseProperties();
        properties.getAndroid().setEnabled(true);
        properties.getAndroid().setMinimumVersionCode(4);
        filter = new AndroidVersionGateFilter(properties, objectMapper);
    }

    @Test
    void rejectsAndroidBuildsBelowTheMinimumWithHttp426() throws Exception {
        MockHttpServletRequest request = request("/api/diaries", "android", "3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("code").asInt()).isEqualTo(ErrorCode.CLIENT_UPGRADE_REQUIRED.getCode());
    }

    @Test
    void allowsBeta4AndWebRequests() throws Exception {
        MockHttpServletResponse beta4 = new MockHttpServletResponse();
        filter.doFilter(request("/api/diaries", "android", "4"), beta4, new MockFilterChain());
        assertThat(beta4.getStatus()).isEqualTo(200);

        MockHttpServletResponse web = new MockHttpServletResponse();
        filter.doFilter(request("/api/diaries", null, null), web, new MockFilterChain());
        assertThat(web.getStatus()).isEqualTo(200);
    }

    @Test
    void neverBlocksBootstrapOrSignedMediaNeededByTheUpdateFlow() throws Exception {
        for (String path : new String[]{"/api/v2/client/bootstrap", "/api/v2/media/public/id/original"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(path, "android", "1"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletRequest request(String path, String platform, String build) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (platform != null) request.addHeader("X-Client-Platform", platform);
        if (build != null) request.addHeader("X-Client-Version-Code", build);
        return request;
    }
}

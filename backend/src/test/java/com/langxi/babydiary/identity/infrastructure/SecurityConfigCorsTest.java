package com.langxi.babydiary.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.langxi.babydiary.platform.api.ClientRequestHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigCorsTest {

    private static final String NATIVE_ORIGIN = "https://localhost";

    @Test
    void androidPreflightAllowsEveryClientRequestHeader() throws Exception {
        var source =
                new SecurityConfig()
                        .corsConfigurationSource("https://diary.example.com", NATIVE_ORIGIN);
        var request = preflight(NATIVE_ORIGIN);
        var response = new MockHttpServletResponse();

        boolean accepted =
                new DefaultCorsProcessor()
                        .processRequest(source.getCorsConfiguration(request), request, response);

        assertThat(accepted).isTrue();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(NATIVE_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase(ClientRequestHeaders.VERSION_NAME)
                .containsIgnoringCase(ClientRequestHeaders.IDEMPOTENCY_KEY);
    }

    @Test
    void androidPreflightRejectsAnUntrustedOrigin() throws Exception {
        var source =
                new SecurityConfig()
                        .corsConfigurationSource("https://diary.example.com", NATIVE_ORIGIN);
        var request = preflight("https://untrusted.example.com");
        var response = new MockHttpServletResponse();

        boolean accepted =
                new DefaultCorsProcessor()
                        .processRequest(source.getCorsConfiguration(request), request, response);

        assertThat(accepted).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest preflight(String origin) {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v3/spaces");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                String.join(",", ClientRequestHeaders.CORS_ALLOWED));
        return request;
    }
}

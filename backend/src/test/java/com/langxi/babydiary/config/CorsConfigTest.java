package com.langxi.babydiary.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void permitsExplicitNativeOriginsWithoutUsingWildcardCredentials() throws Exception {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://diary.example.com");
        ReflectionTestUtils.setField(config, "nativeOrigins", "https://localhost,capacitor://localhost");
        CorsFilter filter = config.corsFilter();

        for (String origin : new String[]{"https://localhost", "capacitor://localhost"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v2/client/bootstrap");
            request.addHeader(HttpHeaders.ORIGIN, origin);
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(origin);
            assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        }
    }
}

package com.langxi.babydiary.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigBaselineTest {

    @Test
    void securityConfigDocumentsPublicPathsAndStatelessJwtBaseline() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/langxi/babydiary/config/SecurityConfig.java")),
                StandardCharsets.UTF_8
        );

        assertThat(source).contains("SessionCreationPolicy.STATELESS");
        assertThat(source).contains("JwtAuthenticationFilter");
        assertThat(source).contains("UsernamePasswordAuthenticationFilter.class");
        assertThat(source).contains("\"/api/auth/login\"");
        assertThat(source).contains("\"/api/auth/register\"");
        assertThat(source).contains("\"/api/v2/client/bootstrap\"");
        assertThat(source).contains("\"/images/**\"");
        assertThat(source).contains("\"/swagger-ui/**\"");
        assertThat(source).contains("\"/v3/api-docs/**\"");
        assertThat(source).contains(".anyRequest().authenticated()");
    }

    @Test
    void productionBackendBindsLoopbackWhileStagingUsesContainerNetworking() throws Exception {
        String productionConfig = Files.readString(
                Paths.get("../config/application-prod.yml"),
                StandardCharsets.UTF_8
        );
        String stagingCompose = Files.readString(
                Paths.get("../compose.staging.yaml"),
                StandardCharsets.UTF_8
        );

        assertThat(productionConfig).contains("address: ${SERVER_ADDRESS:127.0.0.1}");
        assertThat(stagingCompose).contains("SERVER_ADDRESS: 0.0.0.0");
    }
}

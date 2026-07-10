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
        assertThat(source).contains("\"/images/**\"");
        assertThat(source).contains("\"/swagger-ui/**\"");
        assertThat(source).contains("\"/v3/api-docs/**\"");
        assertThat(source).contains(".anyRequest().authenticated()");
    }
}

package com.langxi.babydiary.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final long THIRTY_DAYS_MILLIS = 2592000000L;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", "test-jwt-secret-with-at-least-thirty-two-characters");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", THIRTY_DAYS_MILLIS);
        jwtTokenProvider.init();
    }

    @Test
    void tokenContainsTokenVersionClaim() {
        String token = jwtTokenProvider.generateToken("test-user", 8, 3);

        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("test-user");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(8);
        assertThat(jwtTokenProvider.getTokenVersionFromToken(token)).isEqualTo(3);
    }

    @Test
    void oneMonthExpirationUsesThirtyDaysInMilliseconds() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", THIRTY_DAYS_MILLIS);

        assertThat(jwtTokenProvider.getExpiration()).isEqualTo(THIRTY_DAYS_MILLIS);
    }

    @Test
    void applicationDefaultExpirationIsOneMonth() throws Exception {
        String applicationYaml;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.yml");
             Scanner scanner = new Scanner(inputStream, "UTF-8")) {
            applicationYaml = scanner.useDelimiter("\\A").next();
        }

        assertThat(applicationYaml).contains("expiration: ${JWT_EXPIRATION:" + THIRTY_DAYS_MILLIS + "}");
    }
}

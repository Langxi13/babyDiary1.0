package com.langxi.babydiary.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
class InvitationCodeAdminIntegrationTest {
    private static final String BOOTSTRAP_CODE = "integration-invite-code";
    private static final String PASSWORD = "integration-password";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("baby_diary_invitation_integration")
            .withUsername("baby_diary_test")
            .withPassword("baby_diary_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyStepUpAdministratorCanViewAndRotateTheEncryptedInvitationCode() {
        String adminUsername = "admin-" + shortId();
        register(adminUsername, BOOTSTRAP_CODE);
        String adminToken = login(adminUsername);

        ResponseEntity<JsonNode> missingStepUp = rest.exchange(
                "/api/admin/invitation-code", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), JsonNode.class);
        assertThat(missingStepUp.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        String stepUpToken = stepUp(adminToken);
        HttpHeaders adminHeaders = bearerHeaders(adminToken);
        adminHeaders.set("X-Step-Up-Token", stepUpToken);
        ResponseEntity<JsonNode> visible = rest.exchange(
                "/api/admin/invitation-code", HttpMethod.GET,
                new HttpEntity<>(adminHeaders), JsonNode.class);
        assertSuccess(visible);
        assertThat(visible.getHeaders().getCacheControl()).contains("no-store");
        assertThat(visible.getBody().path("data").path("invitationCode").asText()).isEqualTo(BOOTSTRAP_CODE);
        String encryptedCode = jdbcTemplate.queryForObject(
                "SELECT encrypted_code FROM system_invitation_config WHERE config_id = 1", String.class);
        assertThat(encryptedCode).startsWith("v1:").doesNotContain(BOOTSTRAP_CODE);

        String memberUsername = "member-" + shortId();
        register(memberUsername, BOOTSTRAP_CODE);
        String memberToken = login(memberUsername);
        ResponseEntity<JsonNode> forbidden = rest.exchange(
                "/api/admin/invitation-code", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(memberToken)), JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> rotated = rest.exchange(
                "/api/admin/invitation-code/rotate", HttpMethod.POST,
                new HttpEntity<>(null, adminHeaders), JsonNode.class);
        assertSuccess(rotated);
        assertThat(rotated.getHeaders().getCacheControl()).contains("no-store");
        String newCode = rotated.getBody().path("data").path("invitationCode").asText();
        assertThat(newCode).matches("[A-Za-z0-9_-]{32}").isNotEqualTo(BOOTSTRAP_CODE);

        ResponseEntity<JsonNode> rejectedOldCode = registerResponse("old-code-" + shortId(), BOOTSTRAP_CODE);
        assertThat(rejectedOldCode.getBody().path("code").asInt()).isEqualTo(1004);
        register("new-code-" + shortId(), newCode);
    }

    private void register(String username, String invitationCode) {
        assertSuccess(registerResponse(username, invitationCode));
    }

    private ResponseEntity<JsonNode> registerResponse(String username, String invitationCode) {
        return rest.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", PASSWORD,
                "confirmPassword", PASSWORD,
                "invitationCode", invitationCode
        ), JsonNode.class);
    }

    private String login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Name", "Invitation Integration Test");
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v2/auth/login", new HttpEntity<>(Map.of(
                "username", username,
                "password", PASSWORD
        ), headers), JsonNode.class);
        assertSuccess(response);
        return response.getBody().path("data").path("token").asText();
    }

    private String stepUp(String accessToken) {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/api/v2/auth/step-up",
                new HttpEntity<>(Map.of("password", PASSWORD), bearerHeaders(accessToken)),
                JsonNode.class);
        assertSuccess(response);
        return response.getBody().path("data").path("token").asText();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void assertSuccess(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asInt()).isEqualTo(200);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

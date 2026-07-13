package com.langxi.babydiary.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
class FullStackApiIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("baby_diary_integration")
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
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyProtectedApiMappingRejectsAnonymousRequests() {
        Set<String> apiMappings = new HashSet<>();
        List<String> violations = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            for (String pattern : mapping.getPatternValues()) {
                if (!pattern.startsWith("/api/")) continue;
                Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
                for (RequestMethod method : methods) {
                    String route = method.name() + " " + pattern;
                    apiMappings.add(route);
                    if (isPublicApi(pattern)) continue;
                    String requestPath = pattern.replaceAll("\\{[^/]+}", "1");
                    ResponseEntity<String> response = rest.exchange(
                            requestPath,
                            HttpMethod.valueOf(method.name()),
                            new HttpEntity<>(anonymousJsonHeaders()),
                            String.class
                    );
                    if (response.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                        violations.add(route + " -> " + response.getStatusCode().value());
                    }
                }
            }
        });

        assertThat(apiMappings).hasSizeGreaterThanOrEqualTo(132);
        assertThat(apiMappings).contains(
                "GET /api/albums/groups",
                "GET /api/v2/client/bootstrap",
                "GET /api/v2/spaces",
                "POST /api/v2/spaces/{spaceId}/diaries",
                "POST /api/v2/auth/login"
        );
        assertThat(violations).isEmpty();
    }

    @Test
    void nativeClientBootstrapIsPublicVersionedAndNotCached() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v2/client/bootstrap", JsonNode.class);

        assertSuccess(response);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody().path("data").path("apiVersion").asInt()).isEqualTo(2);
        assertThat(response.getBody().path("data").path("nativeSessionMode").asText()).isEqualTo("COOKIE");
        assertThat(response.getBody().path("data").path("upload").path("maxImageBytes").asLong())
                .isEqualTo(10L * 1024L * 1024L);
    }

    @Test
    void authenticatedAlbumAndSpaceRoutesRunAgainstTheMigratedDatabase() {
        ResponseEntity<JsonNode> legacyUnauthorized = rest.getForEntity("/api/auth/info", JsonNode.class);
        ResponseEntity<JsonNode> v2Unauthorized = rest.getForEntity("/api/v2/spaces", JsonNode.class);
        assertThat(legacyUnauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(v2Unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Session session = registerAndLogin("album-" + shortId());

        HttpHeaders authenticated = bearerHeaders(session.token());
        ResponseEntity<JsonNode> spaces = rest.exchange(
                "/api/v2/spaces", HttpMethod.GET, new HttpEntity<>(authenticated), JsonNode.class);
        ResponseEntity<JsonNode> albums = rest.exchange(
                "/api/albums/groups", HttpMethod.GET, new HttpEntity<>(authenticated), JsonNode.class);

        assertSuccess(spaces);
        assertThat(spaces.getBody().path("data").isArray()).isTrue();
        assertThat(spaces.getBody().path("data").size()).isGreaterThanOrEqualTo(1);
        assertSuccess(albums);
        assertThat(albums.getBody().path("data").isArray()).isTrue();
        assertThat(albums.getBody().path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void diaryLifecycleAndSpaceIsolationUseRealSecurityAndTransactions() {
        Session owner = registerAndLogin("owner-" + shortId());
        Map<String, Object> createPayload = Map.of(
                "clientId", UUID.randomUUID().toString(),
                "title", "集成测试日记",
                "date", "2026-07-11",
                "content", "真实 MySQL 与 HTTP 链路测试内容",
                "contentFormat", "plain",
                "moodKey", "happy",
                "visibility", "SHARED",
                "locked", false
        );
        String diariesPath = "/api/v2/spaces/" + owner.spaceId() + "/diaries";
        ResponseEntity<JsonNode> created = rest.postForEntity(
                diariesPath, new HttpEntity<>(createPayload, bearerHeaders(owner.token())), JsonNode.class);
        assertSuccess(created);
        String diaryId = created.getBody().path("data").path("publicId").asText();
        int legacyDiaryId = created.getBody().path("data").path("diaryId").asInt();
        int version = created.getBody().path("data").path("version").asInt();
        assertThat(diaryId).isNotBlank();

        ResponseEntity<JsonNode> detail = rest.exchange(
                diariesPath + "/" + diaryId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(owner.token())), JsonNode.class);
        assertSuccess(detail);
        assertThat(detail.getBody().path("data").path("title").asText()).isEqualTo("集成测试日记");

        ResponseEntity<JsonNode> legacyList = rest.exchange(
                "/api/diaries?page=0&size=5", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(owner.token())), JsonNode.class);
        ResponseEntity<JsonNode> legacyDetail = rest.exchange(
                "/api/diaries/" + legacyDiaryId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(owner.token())), JsonNode.class);
        assertSuccess(legacyList);
        assertThat(legacyList.getBody().path("data").path("content").size()).isGreaterThanOrEqualTo(1);
        assertSuccess(legacyDetail);
        assertThat(legacyDetail.getBody().path("data").path("diaryId").asInt()).isEqualTo(legacyDiaryId);

        Map<String, Object> updatePayload = Map.of(
                "title", "更新后的集成测试日记",
                "date", "2026-07-12",
                "content", "更新后的内容",
                "contentFormat", "plain",
                "moodKey", "calm",
                "visibility", "SHARED",
                "locked", false,
                "baseVersion", version
        );
        ResponseEntity<JsonNode> updated = rest.exchange(
                diariesPath + "/" + diaryId, HttpMethod.PUT,
                new HttpEntity<>(updatePayload, bearerHeaders(owner.token())), JsonNode.class);
        assertSuccess(updated);
        int updatedVersion = updated.getBody().path("data").path("version").asInt();
        assertThat(updatedVersion).isGreaterThan(version);

        ResponseEntity<JsonNode> search = rest.exchange(
                diariesPath + "?keyword={keyword}&page={page}&size={size}", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(owner.token())), JsonNode.class,
                Map.of("keyword", "更新", "page", 0, "size", 10));
        assertSuccess(search);
        assertThat(search.getBody().path("data").path("content").size()).isEqualTo(1);

        Session outsider = registerAndLogin("outsider-" + shortId());
        ResponseEntity<JsonNode> forbidden = rest.exchange(
                diariesPath + "/" + diaryId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(outsider.token())), JsonNode.class);
        assertThat(forbidden.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);

        HttpHeaders deleteHeaders = bearerHeaders(owner.token());
        deleteHeaders.setIfMatch("\"" + updatedVersion + "\"");
        ResponseEntity<JsonNode> deleted = rest.exchange(
                diariesPath + "/" + diaryId, HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders), JsonNode.class);
        assertSuccess(deleted);

        ResponseEntity<JsonNode> trash = rest.exchange(
                diariesPath + "?trash=true&page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(owner.token())), JsonNode.class);
        assertSuccess(trash);
        JsonNode trashedDiary = trash.getBody().path("data").path("content").get(0);
        assertThat(trashedDiary.path("publicId").asText()).isEqualTo(diaryId);

        HttpHeaders restoreHeaders = bearerHeaders(owner.token());
        restoreHeaders.setIfMatch("\"" + trashedDiary.path("version").asInt() + "\"");
        ResponseEntity<JsonNode> restored = rest.postForEntity(
                diariesPath + "/" + diaryId + "/restore",
                new HttpEntity<>(null, restoreHeaders), JsonNode.class);
        assertSuccess(restored);
        assertThat(restored.getBody().path("data").path("title").asText()).isEqualTo("更新后的集成测试日记");
    }

    private Session registerAndLogin(String username) {
        String password = "integration-password";
        ResponseEntity<JsonNode> registration = rest.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", password,
                "confirmPassword", password,
                "invitationCode", "integration-invite-code"
        ), JsonNode.class);
        assertSuccess(registration);

        ResponseEntity<JsonNode> legacyLogin = rest.postForEntity("/api/auth/login", Map.of(
                "username", username,
                "password", password
        ), JsonNode.class);
        assertSuccess(legacyLogin);
        assertThat(legacyLogin.getBody().path("data").path("token").asText()).isNotBlank();

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-Device-Name", "Integration Test");
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/v2/auth/login", new HttpEntity<>(Map.of(
                "username", username,
                "password", password
        ), loginHeaders), JsonNode.class);
        assertSuccess(login);
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("baby_diary_refresh=");

        String token = login.getBody().path("data").path("token").asText();
        assertThat(token).isNotBlank();
        ResponseEntity<JsonNode> spaces = rest.exchange("/api/v2/spaces", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), JsonNode.class);
        assertSuccess(spaces);
        return new Session(token, spaces.getBody().path("data").get(0).path("spaceId").asText());
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders anonymousJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private boolean isPublicApi(String path) {
        return Set.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/v2/auth/login",
                "/api/v2/auth/refresh",
                "/api/v2/auth/logout",
                "/api/v2/auth/email/confirm",
                "/api/v2/auth/password/reset-request",
                "/api/v2/auth/password/reset",
                "/api/v2/auth/password/recover",
                "/api/v2/client/bootstrap"
        ).contains(path) || path.startsWith("/api/v2/public/shares/") || path.startsWith("/api/v2/media/public/");
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void assertSuccess(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asInt()).isEqualTo(200);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
    }

    private record Session(String token, String spaceId) {
    }
}

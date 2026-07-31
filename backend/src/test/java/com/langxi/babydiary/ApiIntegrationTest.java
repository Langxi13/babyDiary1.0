package com.langxi.babydiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.ai.application.AiClient;
import com.langxi.babydiary.ai.application.AiReportJobHandler;
import com.langxi.babydiary.identity.application.InvitationCodeService;
import com.langxi.babydiary.media.application.StorageGcJobHandler;
import com.langxi.babydiary.platform.infrastructure.BackgroundJobMapper;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = BabyDiaryApplication.class)
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final String PASSWORD = "V3-integration-password";
    private static final String MEDIA_SIGNING_KEY =
            "v3-test-media-url-signing-key-that-is-long-enough-for-hmac";
    private static final UUID OWNER_PUBLIC_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_PUBLIC_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OWNER_SPACE_ID =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OTHER_SPACE_ID =
            UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final String OBJECT_ROOT =
            System.getProperty("java.io.tmpdir") + "/baby-diary-v3-api-" + UUID.randomUUID();

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("baby_diary_api")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "jwt.secret",
                () -> "v3-test-secret-that-is-long-enough-for-hmac-sha-256-signing-key");
        registry.add("app.media.url-signing-key", () -> MEDIA_SIGNING_KEY);
        registry.add("app.rate-limit.enabled", () -> "false");
        registry.add("app.jobs.enabled", () -> "false");
        registry.add("app.cache.enabled", () -> "false");
        registry.add("app.outbox.enabled", () -> "false");
        registry.add("app.ai-schedules.enabled", () -> "false");
        registry.add("app.reminders.enabled", () -> "false");
        registry.add("app.retention.enabled", () -> "false");
        registry.add("app.push.validate-public-dns", () -> "false");
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
        registry.add("app.storage.local-root", () -> OBJECT_ROOT);
        registry.add("ai.config.encryption-key", () -> "v3-test-ai-config-encryption-key");
        registry.add(
                "invitation-code.encryption-key", () -> "v3-test-invitation-code-encryption-key");
        registry.add("app.invitation.bootstrap-code", () -> "v3-test-invitation-code");
    }

    @Autowired MockMvc mvc;

    @Autowired ObjectMapper json;

    @Autowired JdbcTemplate jdbc;

    @Autowired BackgroundJobMapper backgroundJobs;

    @Autowired InvitationCodeService invitationCodes;

    @Autowired StorageGcJobHandler storageGc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappings;

    @Autowired AiReportJobHandler aiReportJobs;

    @MockitoBean AiClient aiClient;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM background_job");
        jdbc.update("DELETE FROM sync_change");
        jdbc.update("DELETE FROM sync_retention");
        jdbc.update("DELETE FROM sync_operation");
        jdbc.update("DELETE FROM ai_report_diary");
        jdbc.update("DELETE FROM ai_report");
        jdbc.update("DELETE FROM ai_album_candidate_media");
        jdbc.update("DELETE FROM ai_album_candidate_diary");
        jdbc.update("DELETE FROM ai_album_candidate");
        jdbc.update("DELETE FROM ai_album_proposal");
        jdbc.update("DELETE FROM ai_config");
        jdbc.update("DELETE FROM space_ai_schedule");
        jdbc.update("DELETE FROM system_invitation_config");
        jdbc.update("DELETE FROM space_invitation");
        jdbc.update("DELETE FROM notification");
        jdbc.update("DELETE FROM push_subscription");
        jdbc.update("DELETE FROM reminder");
        jdbc.update("DELETE FROM diary_revision");
        jdbc.update("DELETE FROM private_share");
        jdbc.update("DELETE FROM diary_comment");
        jdbc.update("DELETE FROM diary_reaction");
        jdbc.update("DELETE FROM diary_tag");
        jdbc.update("DELETE FROM diary_media");
        jdbc.update("DELETE FROM diary_draft");
        jdbc.update("DELETE FROM diary_template WHERE builtin=0");
        jdbc.update("DELETE FROM diary");
        jdbc.update("DELETE FROM tag");
        jdbc.update("DELETE FROM favorite_media");
        jdbc.update("DELETE FROM album_media");
        jdbc.update("DELETE FROM album");
        jdbc.update("DELETE FROM album_group");
        jdbc.update("DELETE FROM anniversary");
        jdbc.update("DELETE FROM user_avatar");
        jdbc.update("DELETE FROM media_variant");
        jdbc.update("DELETE FROM media_asset");
        jdbc.update("DELETE FROM auth_session");
        jdbc.update("DELETE FROM account_token");
        jdbc.update("DELETE FROM recovery_code");
        jdbc.update("DELETE FROM space_storage_usage");
        jdbc.update("DELETE FROM space_member");
        jdbc.update("DELETE FROM diary_space");
        jdbc.update("DELETE FROM account");

        String passwordHash = new BCryptPasswordEncoder().encode(PASSWORD);
        insertAccount(101, OWNER_PUBLIC_ID, "owner", passwordHash);
        insertAccount(202, OTHER_PUBLIC_ID, "other", passwordHash);
        insertAccount(
                303,
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "admin",
                passwordHash,
                "ADMIN");
        insertPersonalSpace(11, OWNER_SPACE_ID, 101, "Owner space");
        insertPersonalSpace(22, OTHER_SPACE_ID, 202, "Other space");
        invitationCodes.run(null);
    }

    @Test
    void loginRefreshRotationAndLogout() throws Exception {
        MvcResult login = login("owner");
        Cookie firstRefresh = login.getResponse().getCookie("baby_diary_refresh");
        mvc.perform(
                        get("/api/v3/auth/sessions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken(login)))
                                .cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceName").value("integration-test"))
                .andExpect(jsonPath("$[0].current").value(true));
        assertThat(firstRefresh).isNotNull();
        assertThat(firstRefresh.isHttpOnly()).isTrue();
        assertThat(firstRefresh.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);

        MvcResult refreshed =
                mvc.perform(post("/api/v3/auth/refresh").cookie(firstRefresh))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").isNotEmpty())
                        .andReturn();
        Cookie secondRefresh = refreshed.getResponse().getCookie("baby_diary_refresh");
        assertThat(secondRefresh).isNotNull();
        assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());

        mvc.perform(post("/api/v3/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        mvc.perform(post("/api/v3/auth/logout").cookie(secondRefresh))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        mvc.perform(post("/api/v3/auth/refresh").cookie(secondRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientBootstrapPublishesUnifiedApiAndProductVersions() throws Exception {
        mvc.perform(get("/api/v3/client/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiVersion").value(3))
                .andExpect(jsonPath("$.serverVersion").value("1.0.0-beta.5"));
    }

    @Test
    void invalidHttpShapesUseStableProblemDetailsInsteadOfInternalErrors() throws Exception {
        mvc.perform(
                        post("/api/v3/auth/login")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_UNSUPPORTED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mvc.perform(get("/api/v3/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mvc.perform(get("/api/v3/client/bootstrap").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("RESPONSE_MEDIA_TYPE_UNSUPPORTED"));

        String token = accessToken(login("owner"));
        mvc.perform(
                        multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void accountRecoveryTokensAreHashedSingleUseAndRevokeExistingSessions() throws Exception {
        MvcResult signedIn = login("owner");
        String accessToken = accessToken(signedIn);
        Cookie refreshCookie = signedIn.getResponse().getCookie("baby_diary_refresh");

        mvc.perform(
                        put("/api/v3/account/email")
                                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("email", "owner-new@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("owner-new@example.com"))
                .andExpect(jsonPath("$.mailSent").value(false));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT LENGTH(token_hash) FROM account_token WHERE account_id=101 AND type='EMAIL_VERIFY'",
                                Integer.class))
                .isEqualTo(32);

        String emailToken = "known-email-verification-token";
        jdbc.update("DELETE FROM account_token WHERE account_id=101 AND type='EMAIL_VERIFY'");
        jdbc.update(
                "INSERT INTO account_token(account_id,type,token_hash,expires_at) VALUES(101,'EMAIL_VERIFY',?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))",
                sha256(emailToken));
        mvc.perform(
                        post("/api/v3/auth/email/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("token", emailToken))))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT email_verified FROM account WHERE account_id=101",
                                Boolean.class))
                .isTrue();

        MvcResult codes =
                mvc.perform(
                                post("/api/v3/auth/recovery-codes")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(8))
                        .andReturn();
        String recoveryCode = body(codes).get(0).asText();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT LENGTH(code_hash) FROM recovery_code WHERE account_id=101 LIMIT 1",
                                Integer.class))
                .isEqualTo(32);

        String nextPassword = "V3-recovered-password";
        mvc.perform(
                        post("/api/v3/auth/password/recover")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        "owner",
                                                        "recoveryCode",
                                                        recoveryCode,
                                                        "newPassword",
                                                        nextPassword))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v3/spaces").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v3/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v3/auth/password/recover")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        "owner",
                                                        "recoveryCode",
                                                        recoveryCode,
                                                        "newPassword",
                                                        nextPassword))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RECOVERY_CODE_INVALID"));
        mvc.perform(
                        post("/api/v3/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        "owner",
                                                        "password",
                                                        nextPassword))))
                .andExpect(status().isOk());
    }

    @Test
    void registrationCreatesAnIsolatedPersonalSpace() throws Exception {
        String adminToken = accessToken(login("admin"));
        MvcResult stepUp =
                mvc.perform(
                                post("/api/v3/auth/step-up")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        MvcResult rotated =
                mvc.perform(
                                post("/api/v3/admin/invitation-code/rotate")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .header(
                                                "X-Step-Up-Token",
                                                body(stepUp).path("token").asText()))
                        .andExpect(status().isOk())
                        .andReturn();

        mvc.perform(
                        post("/api/v3/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        "new-family",
                                                        "password",
                                                        PASSWORD,
                                                        "confirmPassword",
                                                        PASSWORD,
                                                        "invitationCode",
                                                        body(rotated).path("code").asText()))))
                .andExpect(status().isNoContent());

        String token = accessToken(login("new-family"));
        mvc.perform(get("/api/v3/spaces").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    }

    @Test
    void diaryLifecycleUsesEtagsAndEnforcesSpaceIsolation() throws Exception {
        String ownerToken = accessToken(login("owner"));
        mvc.perform(get("/api/v3/spaces").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(OWNER_SPACE_ID.toString()));

        String createBody =
                json.writeValueAsString(
                        Map.of(
                                "title", "First entry",
                                "diaryDate", "2026-07-29",
                                "contentHtml",
                                        "<p>Hello <strong>V3</strong><script>bad()</script></p>",
                                "visibility", "PRIVATE",
                                "locked", true,
                                "tagIds", new String[0],
                                "mediaIds", new String[0]));
        MvcResult created =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                        .andExpect(jsonPath("$.contentText").value("Hello V3"))
                        .andExpect(
                                jsonPath("$.contentHtml")
                                        .value(
                                                org.hamcrest.Matchers.not(
                                                        org.hamcrest.Matchers.containsString(
                                                                "<script>"))))
                        .andReturn();
        UUID diaryId = UUID.fromString(body(created).path("id").asText());
        String stepToken = stepUp(ownerToken);

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(diaryId.toString()));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));

        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .header(HttpHeaders.IF_MATCH, "\"0\"")
                                .header("X-Step-Up-Token", stepToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("DIARY_VERSION_MISMATCH"));

        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .header(HttpHeaders.IF_MATCH, "\"1\"")
                                .header("X-Step-Up-Token", stepToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""));

        String otherToken = accessToken(login("other"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));

        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                        OWNER_SPACE_ID,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .header(HttpHeaders.IF_MATCH, "\"2\"")
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .queryParam("trash", "true")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(diaryId.toString()));

        mvc.perform(
                        post(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/restore",
                                        OWNER_SPACE_ID,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .header(HttpHeaders.IF_MATCH, "\"3\"")
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""));
    }

    @Test
    void protectedEndpointsRejectMissingOrInvalidAccessTokens() throws Exception {
        mvc.perform(get("/api/v3/spaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(get("/api/v3/spaces").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicApiUsesOnlyV3RoutesAndNeverAcceptsInternalNumericPathIds() {
        requestMappings
                .getHandlerMethods()
                .forEach(
                        (mapping, handler) -> {
                            Package controllerPackage = handler.getBeanType().getPackage();
                            if (controllerPackage == null
                                    || !controllerPackage
                                            .getName()
                                            .startsWith("com.langxi.babydiary")
                                    || !controllerPackage.getName().endsWith(".api")) return;
                            assertThat(mapping.getPatternValues())
                                    .allMatch(path -> path.startsWith("/api/v3"));
                            for (java.lang.reflect.Parameter parameter :
                                    handler.getMethod().getParameters()) {
                                if (parameter.getAnnotation(PathVariable.class) == null) continue;
                                assertThat(parameter.getType())
                                        .isNotIn(long.class, Long.class, int.class, Integer.class);
                            }
                        });
    }

    @Test
    void diaryRevisionsUsePublicIdsAndRestoreTheSelectedSnapshot() throws Exception {
        String token = accessToken(login("owner"));
        String first =
                json.writeValueAsString(
                        Map.of(
                                "title", "Revision one",
                                "diaryDate", "2026-07-30",
                                "contentHtml", "<p>First body</p>",
                                "visibility", "PRIVATE",
                                "locked", true,
                                "tagIds", List.of(),
                                "mediaIds", List.of()));
        MvcResult created =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(first))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(created).path("id").asText());
        String stepToken = stepUp(token);
        String second =
                json.writeValueAsString(
                        Map.of(
                                "title", "Revision two",
                                "diaryDate", "2026-07-30",
                                "contentHtml", "<p>Second body</p>",
                                "visibility", "PRIVATE",
                                "locked", false,
                                "tagIds", List.of(),
                                "mediaIds", List.of()));
        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken)
                                .header(HttpHeaders.IF_MATCH, "\"1\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentText").value("Second body"));

        MvcResult revisions =
                mvc.perform(
                                get(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions",
                                                OWNER_SPACE_ID,
                                                diaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].editorId").value(OWNER_PUBLIC_ID.toString()))
                        .andExpect(jsonPath("$[0].editorName").value("owner"))
                        .andReturn();
        JsonNode firstRevision =
                java.util.stream.StreamSupport.stream(body(revisions).spliterator(), false)
                        .filter(value -> value.path("version").asInt() == 1)
                        .findFirst()
                        .orElseThrow();
        UUID revisionId = UUID.fromString(firstRevision.path("id").asText());

        mvc.perform(
                        post(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions/{revisionId}/restore",
                                        OWNER_SPACE_ID,
                                        diaryId,
                                        revisionId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        mvc.perform(
                        post(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/revisions/{revisionId}/restore",
                                        OWNER_SPACE_ID,
                                        diaryId,
                                        revisionId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken)
                                .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.title").value("Revision one"))
                .andExpect(jsonPath("$.contentText").value("First body"))
                .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    void mediaUploadListReadAndDeleteUseTheUnifiedVariantModel() throws Exception {
        String token = accessToken(login("owner"));
        MockMultipartFile file = imageFile("memory.png");

        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(file)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .param("caption", "A test image"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                        .andExpect(
                                jsonPath("$.representations.original.variantType")
                                        .value("ORIGINAL"))
                        .andExpect(jsonPath("$.representations.original.profile").value("source"))
                        .andExpect(
                                jsonPath("$.representations.original.sizeBytes")
                                        .value(file.getSize()))
                        .andReturn();
        UUID assetId = UUID.fromString(body(uploaded).path("id").asText());
        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/media/{assetId}", OWNER_SPACE_ID, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "caption",
                                                        "Updated caption",
                                                        "accessScope",
                                                        "SPACE",
                                                        "libraryVisible",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("Updated caption"))
                .andExpect(jsonPath("$.accessScope").value("SPACE"));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(assetId.toString()));

        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/variants/original",
                                        OWNER_SPACE_ID,
                                        assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_LENGTH,
                                        String.valueOf(file.getSize())));

        mvc.perform(
                        delete("/api/v3/spaces/{spaceId}/media/{assetId}", OWNER_SPACE_ID, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media/{assetId}", OWNER_SPACE_ID, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        storageGc.handle(
                json.valueToTree(
                        Map.of(
                                "spaceId",
                                OWNER_SPACE_ID.toString(),
                                "assetId",
                                assetId.toString())));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT used_bytes FROM space_storage_usage WHERE space_id=11",
                                Long.class))
                .isZero();
    }

    @Test
    void signedMediaUrlsArePublicButCannotBeTamperedWith() throws Exception {
        String token = accessToken(login("owner"));
        MockMultipartFile file = imageFile("signed.png");
        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(file)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        String contentUrl =
                body(uploaded).path("representations").path("original").path("url").asText();
        assertThat(contentUrl).startsWith("/api/v3/public/media/");

        mvc.perform(get(URI.create(contentUrl)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_LENGTH,
                                        String.valueOf(file.getSize())));

        char last = contentUrl.charAt(contentUrl.length() - 1);
        String tampered =
                contentUrl.substring(0, contentUrl.length() - 1) + (last == '0' ? '1' : '0');
        mvc.perform(get(URI.create(tampered)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_URL_INVALID"));
    }

    @Test
    void adaptiveImageRepresentationsRemainReadableAndExportsUseOriginals() throws Exception {
        String token = accessToken(login("owner"));
        byte[] originalBytes = imageBytes();
        byte[] compactBytes = imageBytes();
        byte[] previewBytes = imageBytes();
        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        "migrated.png",
                                                        "image/png",
                                                        originalBytes))
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(uploaded).path("id").asText());
        Long internalId = setOriginalProfile(assetId, "source");

        String compactKey = "v3/test/" + assetId + "/compact.webp";
        Path compact = Path.of(OBJECT_ROOT).resolve(compactKey);
        Files.createDirectories(compact.getParent());
        Files.write(compact, compactBytes);
        jdbc.update(
                """
                INSERT INTO media_variant(asset_id,variant_type,profile,storage_provider,storage_key,
                  content_type,size_bytes,quality_score,status)
                VALUES(?,'THUMBNAIL','compact','LOCAL',?,'image/webp',?,1.0,'READY')
                """,
                internalId,
                compactKey,
                compactBytes.length);
        String previewKey = "v3/test/" + assetId + "/screen.webp";
        Path preview = Path.of(OBJECT_ROOT).resolve(previewKey);
        Files.write(preview, previewBytes);
        jdbc.update(
                """
                INSERT INTO media_variant(asset_id,variant_type,profile,storage_provider,storage_key,
                  content_type,size_bytes,quality_score,status)
                VALUES(?,'PREVIEW','screen','LOCAL',?,'image/webp',?,1.0,'READY')
                """,
                internalId,
                previewKey,
                previewBytes.length);

        MvcResult created =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Migrated media",
                                                                "diaryDate",
                                                                "2026-07-30",
                                                                "contentHtml",
                                                                "<p>Profile-aware media</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(created).path("id").asText());
        MvcResult detail =
                mvc.perform(
                                get(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                                OWNER_SPACE_ID,
                                                diaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.media[0].representations.original.url")
                                        .value(
                                                org.hamcrest.Matchers.containsString(
                                                        "profile=source")))
                        .andExpect(
                                jsonPath("$.media[0].representations.thumbnail.url")
                                        .value(
                                                org.hamcrest.Matchers.containsString(
                                                        "profile=compact")))
                        .andExpect(
                                jsonPath("$.media[0].representations.preview.url")
                                        .value(
                                                org.hamcrest.Matchers.containsString(
                                                        "profile=screen")))
                        .andReturn();
        String contentUrl =
                body(detail)
                        .path("media")
                        .get(0)
                        .path("representations")
                        .path("original")
                        .path("url")
                        .asText();
        String compactUrl =
                body(detail)
                        .path("media")
                        .get(0)
                        .path("representations")
                        .path("thumbnail")
                        .path("url")
                        .asText();
        String previewUrl =
                body(detail)
                        .path("media")
                        .get(0)
                        .path("representations")
                        .path("preview")
                        .path("url")
                        .asText();

        mvc.perform(get(URI.create(contentUrl)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_LENGTH,
                                        String.valueOf(originalBytes.length)));
        mvc.perform(get(URI.create(compactUrl)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_LENGTH,
                                        String.valueOf(compactBytes.length)));
        mvc.perform(get(URI.create(previewUrl)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_LENGTH,
                                        String.valueOf(previewBytes.length)));
        MvcResult imageExport =
                mvc.perform(
                                get("/api/v3/spaces/{spaceId}/transfer/media", OWNER_SPACE_ID)
                                        .queryParam("startDate", "2026-07-30")
                                        .queryParam("endDate", "2026-07-30")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                        .andReturn();
        assertThat(zipEntries(imageExport.getResponse().getContentAsByteArray()))
                .singleElement()
                .asString()
                .contains(assetId.toString(), "migrated.png");
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/variants/original",
                                        OWNER_SPACE_ID,
                                        assetId)
                                .queryParam("profile", "source")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/variants/original",
                                        OWNER_SPACE_ID,
                                        assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/variants/original",
                                        OWNER_SPACE_ID,
                                        assetId)
                                .queryParam("profile", "default")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_VARIANT_NOT_FOUND"));
        mvc.perform(get(URI.create(contentUrl.replace("profile=source", "profile=default"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_URL_INVALID"));

        mvc.perform(get(URI.create(legacyMediaUrl(OWNER_SPACE_ID, assetId, "ORIGINAL"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void privateSharesProtectPasswordsViewsAndSignedMediaWithoutAuthentication() throws Exception {
        String token = accessToken(login("owner"));
        MockMultipartFile file = imageFile("shared.png");
        MvcResult upload =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(file)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(upload).path("id").asText());
        setOriginalProfile(assetId, "source");
        MvcResult diary =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Shared privately",
                                                                "diaryDate",
                                                                "2026-07-29",
                                                                "contentHtml",
                                                                "<p>Private content</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(diary).path("id").asText());
        MvcResult created =
                mvc.perform(
                                post(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}/shares",
                                                OWNER_SPACE_ID,
                                                diaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "expiresInHours",
                                                                24,
                                                                "password",
                                                                "share-pass",
                                                                "maxViews",
                                                                1))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String sharePath = body(created).path("sharePath").asText();
        String rawToken = sharePath.substring(sharePath.lastIndexOf('/') + 1);

        mvc.perform(
                        post("/api/v3/public/shares/{token}/open", rawToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SHARE_PASSWORD_INVALID"));
        MvcResult opened =
                mvc.perform(
                                post("/api/v3/public/shares/{token}/open", rawToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"password\":\"share-pass\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("Shared privately"))
                        .andExpect(jsonPath("$.media[0].id").value(assetId.toString()))
                        .andExpect(
                                jsonPath(
                                        "$.media[0].representations.original.url",
                                        org.hamcrest.Matchers.containsString("profile=source")))
                        .andReturn();
        mvc.perform(
                        get(
                                URI.create(
                                        body(opened)
                                                .path("media")
                                                .get(0)
                                                .path("representations")
                                                .path("original")
                                                .path("url")
                                                .asText())))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v3/public/shares/{token}/open", rawToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"share-pass\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mediaProtectedByAnyLockedDiaryCannotEscapeThroughSharesOrExports() throws Exception {
        String token = accessToken(login("owner"));
        MvcResult upload =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(imageFile("cross-protected.png"))
                                        .param("caption", "private caption")
                                        .param("takenAt", "2026-07-30T10:00:00")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(upload).path("id").asText());

        MvcResult ordinary =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Ordinary memory",
                                                                "diaryDate",
                                                                "2026-07-30",
                                                                "contentHtml",
                                                                "<p>ordinary</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID ordinaryId = UUID.fromString(body(ordinary).path("id").asText());
        MvcResult share =
                mvc.perform(
                                post(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}/shares",
                                                OWNER_SPACE_ID,
                                                ordinaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"expiresInHours\":24}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String sharePath = body(share).path("sharePath").asText();
        String shareToken = sharePath.substring(sharePath.lastIndexOf('/') + 1);

        MvcResult locked =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Locked relation",
                                                                "diaryDate",
                                                                "2026-07-29",
                                                                "contentHtml",
                                                                "<p>locked</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                true,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID lockedId = UUID.fromString(body(locked).path("id").asText());
        int lockedVersion = body(locked).path("version").asInt();

        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                        OWNER_SPACE_ID,
                                        ordinaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].protectedContent").value(true))
                .andExpect(jsonPath("$.media[0].caption").value(nullValue()))
                .andExpect(jsonPath("$.media[0].takenAt").value(nullValue()))
                .andExpect(
                        jsonPath("$.media[0].representations.original.contentType")
                                .value(nullValue()))
                .andExpect(
                        jsonPath("$.media[0].representations.original.sizeBytes")
                                .value(nullValue()));
        mvc.perform(
                        post("/api/v3/public/shares/{token}/open", shareToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_FOUND"));
        mvc.perform(
                        post(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/shares",
                                        OWNER_SPACE_ID,
                                        ordinaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expiresInHours\":24}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCKED_CONTENT_NOT_SHAREABLE"));

        String stepToken = stepUp(token);
        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                        OWNER_SPACE_ID,
                                        lockedId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken)
                                .header(HttpHeaders.IF_MATCH, "\"" + lockedVersion + "\""))
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/transfer/export", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/transfer/media", OWNER_SPACE_ID)
                                .queryParam("startDate", "2026-07-30")
                                .queryParam("endDate", "2026-07-30")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/transfer/export", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/transfer/media", OWNER_SPACE_ID)
                                .queryParam("startDate", "2026-07-30")
                                .queryParam("endDate", "2026-07-30")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
    }

    @Test
    void linkedAndSpaceMediaScopesRespectDirectParentAndLibraryAccess() throws Exception {
        String ownerToken = accessToken(login("owner"));
        String otherToken = accessToken(login("other"));
        UUID sharedSpaceId = createSharedSpaceWithOther(ownerToken, otherToken);

        MvcResult upload =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", sharedSpaceId)
                                        .file(imageFile("shared-scope.png"))
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.accessScope").value("LINKED"))
                        .andExpect(jsonPath("$.libraryVisible").value(true))
                        .andReturn();
        UUID assetId = UUID.fromString(body(upload).path("id").asText());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media/{assetId}", sharedSpaceId, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/system/all", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media").isEmpty());

        MvcResult diary =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", sharedSpaceId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Shared parent",
                                                                "diaryDate",
                                                                "2026-07-30",
                                                                "contentHtml",
                                                                "<p>Parent grants read access.</p>",
                                                                "visibility",
                                                                "SHARED",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(diary).path("id").asText());
        MvcResult otherView =
                mvc.perform(
                                get(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                                sharedSpaceId,
                                                diaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.media[0].id").value(assetId.toString()))
                        .andReturn();
        mvc.perform(
                        get(
                                URI.create(
                                        body(otherView)
                                                .path("media")
                                                .get(0)
                                                .path("representations")
                                                .path("original")
                                                .path("url")
                                                .asText())))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/diaries", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Illegal reuse",
                                                        "diaryDate",
                                                        "2026-07-30",
                                                        "contentHtml",
                                                        "<p>Must not reuse linked media.</p>",
                                                        "visibility",
                                                        "SHARED",
                                                        "locked",
                                                        false,
                                                        "tagIds",
                                                        List.of(),
                                                        "mediaIds",
                                                        List.of(assetId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));

        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/media/{assetId}", sharedSpaceId, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "caption",
                                                        "Reusable",
                                                        "accessScope",
                                                        "SPACE",
                                                        "libraryVisible",
                                                        true))))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media/{assetId}", sharedSpaceId, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/system/all", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].id").value(assetId.toString()));
        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/diaries", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Allowed reuse",
                                                        "diaryDate",
                                                        "2026-07-30",
                                                        "contentHtml",
                                                        "<p>Space media can be reused.</p>",
                                                        "visibility",
                                                        "SHARED",
                                                        "locked",
                                                        false,
                                                        "tagIds",
                                                        List.of(),
                                                        "mediaIds",
                                                        List.of(assetId)))))
                .andExpect(status().isCreated());
    }

    @Test
    void lockedDiariesAndTheirMediaRequireStepUpWithoutLeakingSearchContent() throws Exception {
        String token = accessToken(login("owner"));
        MvcResult upload =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(imageFile("locked.png"))
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(upload).path("id").asText());
        MvcResult created =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "vaultsecret",
                                                                "diaryDate",
                                                                "2026-07-30",
                                                                "contentHtml",
                                                                "<p>private locked content</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                true,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(created).path("id").asText());

        MvcResult list =
                mvc.perform(
                                get("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].locked").value(true))
                        .andExpect(jsonPath("$.items[0].media").isEmpty())
                        .andReturn();
        assertThat(body(list).path("items").get(0).path("title").isNull()).isTrue();
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .queryParam("keyword", "vaultsecret")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].protectedContent").value(true))
                .andExpect(jsonPath("$.items[0].originalFilename").value(nullValue()))
                .andExpect(jsonPath("$.items[0].caption").value(nullValue()))
                .andExpect(jsonPath("$.items[0].takenAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].representations.original.url").value(nullValue()))
                .andExpect(
                        jsonPath("$.items[0].representations.original.contentType")
                                .value(nullValue()))
                .andExpect(
                        jsonPath("$.items[0].representations.original.sizeBytes")
                                .value(nullValue()));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/{diaryId}", OWNER_SPACE_ID, diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media/{assetId}", OWNER_SPACE_ID, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        mvc.perform(
                        post(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/shares",
                                        OWNER_SPACE_ID,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("expiresInHours", 24))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCKED_CONTENT_NOT_SHAREABLE"));

        String stepToken = stepUp(token);
        MvcResult detail =
                mvc.perform(
                                get(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                                OWNER_SPACE_ID,
                                                diaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .header("X-Step-Up-Token", stepToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("vaultsecret"))
                        .andExpect(jsonPath("$.media[0].representations.original.url").isNotEmpty())
                        .andReturn();
        mvc.perform(
                        get(
                                URI.create(
                                        body(detail)
                                                .path("media")
                                                .get(0)
                                                .path("representations")
                                                .path("original")
                                                .path("url")
                                                .asText())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/media/{assetId}", OWNER_SPACE_ID, assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("locked.png"))
                .andExpect(jsonPath("$.representations.original.url").isNotEmpty());
        mvc.perform(
                        put("/api/v3/account/avatar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "spaceId",
                                                        OWNER_SPACE_ID.toString(),
                                                        "assetId",
                                                        assetId.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_MEDIA_PROTECTED"));

        MvcResult lockedAlbum =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/albums", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .header("X-Step-Up-Token", stepToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Locked album",
                                                                "mediaIds",
                                                                List.of(assetId.toString())))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID lockedAlbumId = UUID.fromString(body(lockedAlbum).path("id").asText());
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/favorite",
                                        OWNER_SPACE_ID,
                                        assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isNoContent());
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/albums/{albumId}",
                                        OWNER_SPACE_ID,
                                        lockedAlbumId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("name", "Renamed locked"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaCount").value(0))
                .andExpect(jsonPath("$.coverAssetId").value(nullValue()))
                .andExpect(jsonPath("$.coverMedia").value(nullValue()));
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/albums/{albumId}",
                                        OWNER_SPACE_ID,
                                        lockedAlbumId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("name", "Renamed locked"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaCount").value(1))
                .andExpect(jsonPath("$.coverAssetId").value(assetId.toString()))
                .andExpect(jsonPath("$.coverMedia.representations.original.url").isNotEmpty());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/album-groups", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].albums[0].mediaCount").value(0))
                .andExpect(jsonPath("$.groups[0].albums[1].mediaCount").value(0))
                .andExpect(jsonPath("$.groups[0].albums.length()").value(2))
                .andExpect(jsonPath("$.groups[1].albums[0].mediaCount").value(0))
                .andExpect(jsonPath("$.groups[1].albums[0].coverAssetId").value(nullValue()))
                .andExpect(jsonPath("$.groups[1].albums[0].coverMedia").value(nullValue()));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/system/all", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMedia").value(0))
                .andExpect(jsonPath("$.media").isEmpty());
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/albums/{albumId}",
                                        OWNER_SPACE_ID,
                                        lockedAlbumId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.album.mediaCount").value(0))
                .andExpect(jsonPath("$.album.coverAssetId").value(nullValue()))
                .andExpect(jsonPath("$.media").isEmpty());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/album-groups", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].albums[0].mediaCount").value(1))
                .andExpect(jsonPath("$.groups[0].albums[1].mediaCount").value(1))
                .andExpect(jsonPath("$.groups[0].albums[2].systemKey").value("year:2026"))
                .andExpect(jsonPath("$.groups[1].albums[0].mediaCount").value(1))
                .andExpect(
                        jsonPath("$.groups[1].albums[0].coverAssetId").value(assetId.toString()));
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/albums/{albumId}",
                                        OWNER_SPACE_ID,
                                        lockedAlbumId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("X-Step-Up-Token", stepToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMedia").value(1))
                .andExpect(jsonPath("$.media[0].id").value(assetId.toString()))
                .andExpect(jsonPath("$.media[0].originalFilename").value("locked.png"))
                .andExpect(jsonPath("$.media[0].representations.original.url").isNotEmpty());
    }

    @Test
    void tagsAreScopedToTheActiveSpaceAndCanBeUsedByDiaryWrites() throws Exception {
        String token = accessToken(login("owner"));
        MvcResult createdTag =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/tags", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name", "Trip", "color",
                                                                "#12ABef"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("Trip"))
                        .andExpect(jsonPath("$.color").value("#12ABef"))
                        .andReturn();
        UUID tagId = UUID.fromString(body(createdTag).path("id").asText());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/tags", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tagId.toString()));

        String diary =
                json.writeValueAsString(
                        Map.of(
                                "title",
                                "Tagged entry",
                                "diaryDate",
                                "2026-07-29",
                                "contentHtml",
                                "<p>Tagged</p>",
                                "visibility",
                                "PRIVATE",
                                "locked",
                                false,
                                "tagIds",
                                List.of(tagId.toString()),
                                "mediaIds",
                                List.of()));
        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(diary))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags[0].id").value(tagId.toString()));

        String otherToken = accessToken(login("other"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/tags", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sharedDiaryCommentsAndReactionsAreScopedAndAuthorControlled() throws Exception {
        String ownerToken = accessToken(login("owner"));
        MvcResult createdSpace =
                mvc.perform(
                                post("/api/v3/spaces")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Interaction space",
                                                                "defaultVisibility",
                                                                "SHARED"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID sharedSpaceId = UUID.fromString(body(createdSpace).path("id").asText());
        MvcResult invitation =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/invitations", sharedSpaceId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                "other@example.com",
                                                                "role",
                                                                "MEMBER"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String otherToken = accessToken(login("other"));
        mvc.perform(
                        post(
                                        "/api/v3/invitations/{token}/accept",
                                        body(invitation).path("token").asText())
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNoContent());

        MvcResult diary =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", sharedSpaceId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Shared memory",
                                                                "diaryDate",
                                                                "2026-07-29",
                                                                "contentHtml",
                                                                "<p>Together</p>",
                                                                "visibility",
                                                                "SHARED",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of()))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(diary).path("id").asText());
        MvcResult comment =
                mvc.perform(
                                post(
                                                "/api/v3/spaces/{spaceId}/diaries/{diaryId}/comments",
                                                sharedSpaceId,
                                                diaryId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("content", "A shared response"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.authorId").value(OTHER_PUBLIC_ID.toString()))
                        .andReturn();
        UUID commentId = UUID.fromString(body(comment).path("id").asText());

        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/comments/{commentId}",
                                        sharedSpaceId,
                                        diaryId,
                                        commentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("content", "Not the author"))))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/reactions",
                                        sharedSpaceId,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("emoji", "👍", "active", true))))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/reactions",
                                        sharedSpaceId,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emoji").value("👍"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].reactedByMe").value(true));

        UUID privateDiaryId = createDiary(ownerToken, "Private memory", "2026-07-30");
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/comments",
                                        OWNER_SPACE_ID,
                                        privateDiaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void albumCatalogIncludesSystemFavoritesAndCustomAlbumDetails() throws Exception {
        String token = accessToken(login("owner"));
        MockMultipartFile file = imageFile("album.png");
        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(file)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(uploaded).path("id").asText());
        setOriginalProfile(assetId, "source");
        MockMultipartFile secondFile = imageFile("album-second.png");
        MvcResult secondUpload =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(secondFile)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID secondAssetId = UUID.fromString(body(secondUpload).path("id").asText());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/album-groups", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].albums[0].systemKey").value("all"))
                .andExpect(jsonPath("$.groups[0].albums[0].mediaCount").value(2))
                .andExpect(jsonPath("$.groups[0].albums[0].coverAssetId").isNotEmpty())
                .andExpect(jsonPath("$.groups[0].albums[0].coverMedia.id").isNotEmpty())
                .andExpect(jsonPath("$.groups[0].albums[1].systemKey").value("favorites"))
                .andExpect(jsonPath("$.groups[0].albums[1].mediaCount").value(0))
                .andExpect(jsonPath("$.groups[0].albums[2].systemKey").value("year:2026"))
                .andExpect(jsonPath("$.groups[0].albums[2].mediaCount").value(2))
                .andExpect(jsonPath("$.groups[0].albums[2].coverMedia.id").isNotEmpty());

        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/favorite",
                                        OWNER_SPACE_ID,
                                        assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/media/{assetId}/favorite",
                                        OWNER_SPACE_ID,
                                        secondAssetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        MvcResult group =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/album-groups", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(Map.of("name", "Trips"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID groupId = UUID.fromString(body(group).path("id").asText());

        MvcResult album =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/albums", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "groupId",
                                                                groupId.toString(),
                                                                "name",
                                                                "Europe",
                                                                "description",
                                                                "Summer trip",
                                                                "mediaIds",
                                                                List.of(
                                                                        assetId.toString(),
                                                                        secondAssetId
                                                                                .toString())))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.coverAssetId").value(assetId.toString()))
                        .andExpect(
                                jsonPath(
                                        "$.coverMedia.representations.original.url",
                                        org.hamcrest.Matchers.containsString("profile=source")))
                        .andExpect(jsonPath("$.mediaCount").value(2))
                        .andReturn();
        UUID albumId = UUID.fromString(body(album).path("id").asText());
        jdbc.update("UPDATE album SET cover_asset_id=NULL WHERE public_id=?", uuid(albumId));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/album-groups", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].albums[1].coverMedia.id").isNotEmpty())
                .andExpect(jsonPath("$.groups[1].albums[0].coverAssetId").value(assetId.toString()))
                .andExpect(
                        jsonPath("$.groups[1].albums[0].coverMedia.id").value(assetId.toString()));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/{albumId}", OWNER_SPACE_ID, albumId)
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.album.groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.album.coverAssetId").value(assetId.toString()))
                .andExpect(jsonPath("$.album.coverMedia.id").value(assetId.toString()))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(assetId.toString()))
                .andExpect(jsonPath("$.totalMedia").value(2))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/{albumId}", OWNER_SPACE_ID, albumId)
                                .queryParam("page", "1")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(secondAssetId.toString()))
                .andExpect(jsonPath("$.totalMedia").value(2));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/system/favorites", OWNER_SPACE_ID)
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.totalMedia").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/system/all", OWNER_SPACE_ID)
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.totalMedia").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/system/year:2026", OWNER_SPACE_ID)
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.album.systemKey").value("year:2026"))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.totalMedia").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/albums/{albumId}/media/{assetId}",
                                        OWNER_SPACE_ID,
                                        albumId,
                                        assetId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/albums/{albumId}", OWNER_SPACE_ID, albumId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(secondAssetId.toString()))
                .andExpect(jsonPath("$.totalMedia").value(1))
                .andExpect(jsonPath("$.album.coverAssetId").value(secondAssetId.toString()));
    }

    @Test
    void draftSaveUpdateAndDeleteRemainPrivateToTheOwner() throws Exception {
        String token = accessToken(login("owner"));
        String first =
                json.writeValueAsString(
                        Map.of(
                                "payload",
                                Map.of(
                                        "title",
                                        "Draft title",
                                        "diaryDate",
                                        "2026-07-29",
                                        "contentHtml",
                                        "<p>Draft</p>")));
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/drafts/{draftKey}",
                                        OWNER_SPACE_ID,
                                        "new-entry")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftKey").value("new-entry"))
                .andExpect(jsonPath("$.payload.title").value("Draft title"));

        String second =
                json.writeValueAsString(
                        Map.of(
                                "payload",
                                Map.of(
                                        "title",
                                        "Updated draft",
                                        "diaryDate",
                                        "2026-07-29",
                                        "contentHtml",
                                        "<p>Updated</p>")));
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/drafts/{draftKey}",
                                        OWNER_SPACE_ID,
                                        "new-entry")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.title").value("Updated draft"));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/drafts", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].draftKey").value("new-entry"));

        String otherToken = accessToken(login("other"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/drafts", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/drafts/{draftKey}",
                                        OWNER_SPACE_ID,
                                        "new-entry")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/drafts/{draftKey}",
                                        OWNER_SPACE_ID,
                                        "new-entry")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anniversaryCoverUsesTheSameMediaAssetAcrossCreateUpdateAndDelete() throws Exception {
        String token = accessToken(login("owner"));
        MockMultipartFile file = imageFile("cover.png");
        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(file)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(uploaded).path("id").asText());

        MvcResult created =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/anniversaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "First day",
                                                                "date",
                                                                "2026-01-01",
                                                                "description",
                                                                "Memory",
                                                                "coverAssetId",
                                                                assetId.toString(),
                                                                "sortOrder",
                                                                0))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.coverAssetId").value(assetId.toString()))
                        .andExpect(jsonPath("$.coverMedia.id").value(assetId.toString()))
                        .andReturn();
        UUID anniversaryId = UUID.fromString(body(created).path("id").asText());

        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/anniversaries/{anniversaryId}",
                                        OWNER_SPACE_ID,
                                        anniversaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Updated day",
                                                        "date",
                                                        "2026-01-02",
                                                        "description",
                                                        "Updated",
                                                        "coverAssetId",
                                                        assetId.toString(),
                                                        "sortOrder",
                                                        1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated day"));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/anniversaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(anniversaryId.toString()));

        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/anniversaries/{anniversaryId}",
                                        OWNER_SPACE_ID,
                                        anniversaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/anniversaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void calendarAndTimelineUseLightweightAggregatesInsteadOfFullDiaryBodies() throws Exception {
        String token = accessToken(login("owner"));
        createDiary(token, "A very long calendar title that must be shortened", "2026-07-01");
        createDiary(token, "Second", "2026-07-09");
        createDiary(token, "August", "2026-08-02");

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/calendar", OWNER_SPACE_ID)
                                .queryParam("month", "2026-07")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-07"))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].entries[0].title").value("A very long calend..."));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/timeline", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].count").value(3))
                .andExpect(jsonPath("$.years[0].months.length()").value(2));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries/timeline/weeks", OWNER_SPACE_ID)
                                .queryParam("month", "2026-07")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void templatesSearchInsightsAndAiScheduleUseV3DataAndPrivacyRules() throws Exception {
        String token = accessToken(login("owner"));
        MvcResult template =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/templates", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Family review",
                                                                "description",
                                                                "Weekly prompts",
                                                                "icon",
                                                                "Notebook",
                                                                "promptText",
                                                                "Remember the small things",
                                                                "contentHtml",
                                                                "<h2>Highlights</h2><script>bad()</script><p>Write here</p>"))))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath(
                                        "$.contentHtml",
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("script"))))
                        .andExpect(jsonPath("$.editable").value(true))
                        .andReturn();
        UUID templateId = UUID.fromString(body(template).path("id").asText());
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/templates/{templateId}",
                                        OWNER_SPACE_ID,
                                        templateId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "name",
                                                        "Updated review",
                                                        "contentHtml",
                                                        "<p>Updated prompt</p>"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated review"));

        createDiary(token, "Summer family memory", "2026-07-01");
        createDiary(token, "Summer family picnic", "2026-07-02");
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/search", OWNER_SPACE_ID)
                                .queryParam("query", "Summer family")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].entityType").value("DIARY"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/insights/yearly", OWNER_SPACE_ID)
                                .queryParam("year", "2026")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaryCount").value(2))
                .andExpect(jsonPath("$.activeDays").value(2))
                .andExpect(jsonPath("$.months[0].month").value("2026-07"));

        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/ai/schedule", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "weeklyEnabled",
                                                        true,
                                                        "monthlyEnabled",
                                                        true,
                                                        "annualEnabled",
                                                        false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyEnabled").value(true))
                .andExpect(jsonPath("$.nextRunAt").isNotEmpty());
        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/templates/{templateId}",
                                        OWNER_SPACE_ID,
                                        templateId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aiConfigIsAdminOnlyAndReportsUseTheConfiguredSecondPersonPrompt() throws Exception {
        String adminToken = accessToken(login("admin"));
        mvc.perform(
                        post("/api/v3/admin/ai")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "enabled",
                                                        true,
                                                        "baseUrl",
                                                        "https://ai.example.com/v1",
                                                        "model",
                                                        "stable-model",
                                                        "apiKey",
                                                        "secret-value",
                                                        "timeoutSeconds",
                                                        20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.hasApiKey").value(true));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT encrypted_api_key FROM ai_config WHERE config_id=1",
                                String.class))
                .doesNotContain("secret-value");
        MvcResult stepUp =
                mvc.perform(
                                post("/api/v3/auth/step-up")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        String stepUpToken = body(stepUp).path("token").asText();
        MvcResult rotatedCode =
                mvc.perform(
                                post("/api/v3/admin/invitation-code/rotate")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .header("X-Step-Up-Token", stepUpToken))
                        .andExpect(status().isOk())
                        .andReturn();
        String invitationCode = body(rotatedCode).path("code").asText();
        mvc.perform(
                        post("/api/v3/admin/invitation-code/view")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .header("X-Step-Up-Token", stepUpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(invitationCode));

        String ownerToken = accessToken(login("owner"));
        createDiary(ownerToken, "A family day", "2026-07-29");
        when(aiClient.generate(any(), any())).thenReturn("# 七月回顾\n\n在这个月中，你们一起走过了温柔的一天。");

        MvcResult report =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/ai-reports", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "type", "MONTHLY", "period",
                                                                "2026-07"))))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.contentMarkdown")
                                        .value("# 七月回顾\n\n在这个月中，你们一起走过了温柔的一天。"))
                        .andExpect(jsonPath("$.diaryCount").value(1))
                        .andReturn();
        UUID reportId = UUID.fromString(body(report).path("id").asText());

        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/ai-reports/{reportId}",
                                        OWNER_SPACE_ID,
                                        reportId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodType").value("MONTHLY"));

        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/ai-reports", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("type", "ANNUAL", "period", "2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodType").value("ANNUAL"))
                .andExpect(jsonPath("$.start").value("2026-01-01"))
                .andExpect(jsonPath("$.end").value("2026-12-31"));

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/ai-reports", OWNER_SPACE_ID)
                                .queryParam("type", "MONTHLY")
                                .queryParam("page", "0")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/ai-reports", OWNER_SPACE_ID)
                                .queryParam("page", "1")
                                .queryParam("size", "1")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        JsonNode existing =
                aiReportJobs.handle(
                        json.valueToTree(
                                Map.of(
                                        "spaceId",
                                        OWNER_SPACE_ID.toString(),
                                        "accountId",
                                        101,
                                        "type",
                                        "MONTHLY",
                                        "period",
                                        "2026-07")));
        assertThat(existing.path("reportId").asText()).isEqualTo(reportId.toString());
        verify(aiClient, times(2)).generate(any(), any());

        mvc.perform(
                        post("/api/v3/admin/ai/test")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void aiAlbumProposalDerivesMediaFromDiariesAndConfirmsWithoutCopyingAssets() throws Exception {
        String adminToken = accessToken(login("admin"));
        mvc.perform(
                        post("/api/v3/admin/ai")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "enabled",
                                                        true,
                                                        "baseUrl",
                                                        "https://ai.example.com/v1",
                                                        "model",
                                                        "stable-model",
                                                        "apiKey",
                                                        "secret-value",
                                                        "timeoutSeconds",
                                                        20))))
                .andExpect(status().isOk());

        String token = accessToken(login("owner"));
        MockMultipartFile file = imageFile("europe.png");
        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(file)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(uploaded).path("id").asText());
        MvcResult diary =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "Europe trip",
                                                                "diaryDate",
                                                                "2026-07-15",
                                                                "contentHtml",
                                                                "<p>Travelled through Europe together.</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of(assetId.toString())))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID diaryId = UUID.fromString(body(diary).path("id").asText());
        when(aiClient.generate(any(), any()))
                .thenReturn(
                        json.writeValueAsString(
                                Map.of(
                                        "albums",
                                        List.of(
                                                Map.of(
                                                        "mode",
                                                        "NEW",
                                                        "title",
                                                        "欧洲旅行",
                                                        "description",
                                                        "旅途记录",
                                                        "diaryIds",
                                                        List.of(diaryId.toString()))))));

        MvcResult proposal =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/ai-album-proposals", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "startDate",
                                                                "2026-07-01",
                                                                "endDate",
                                                                "2026-07-31"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.albums[0].diaryIds[0]").value(diaryId.toString()))
                        .andExpect(jsonPath("$.albums[0].assetIds[0]").value(assetId.toString()))
                        .andReturn();
        UUID proposalId = UUID.fromString(body(proposal).path("id").asText());

        jdbc.update("UPDATE diary SET locked=1 WHERE public_id=?", uuid(diaryId));
        mvc.perform(
                        get(
                                        "/api/v3/spaces/{spaceId}/ai-album-proposals/{proposalId}",
                                        OWNER_SPACE_ID,
                                        proposalId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums[0].diaryIds").isEmpty())
                .andExpect(jsonPath("$.albums[0].assetIds").isEmpty())
                .andExpect(jsonPath("$.albums[0].photos").isEmpty());
        jdbc.update("UPDATE diary SET locked=0 WHERE public_id=?", uuid(diaryId));

        mvc.perform(
                        post(
                                        "/api/v3/spaces/{spaceId}/ai-album-proposals/{proposalId}/confirm",
                                        OWNER_SPACE_ID,
                                        proposalId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM album WHERE space_id=11 AND type='AI' AND deleted_at IS NULL",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM album_media WHERE space_id=11",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM media_asset WHERE space_id=11",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void profileAvatarAndSpaceInvitationRemainAccountScoped() throws Exception {
        String ownerToken = accessToken(login("owner"));
        mvc.perform(
                        get("/api/v3/account/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        MvcResult createdSpace =
                mvc.perform(
                                post("/api/v3/spaces")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Shared family space",
                                                                "defaultVisibility",
                                                                "SHARED"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID sharedSpaceId = UUID.fromString(body(createdSpace).path("id").asText());
        mvc.perform(
                        put("/api/v3/spaces/{spaceId}", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "name",
                                                        "Renamed family space",
                                                        "defaultVisibility",
                                                        "PRIVATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed family space"))
                .andExpect(jsonPath("$.defaultVisibility").value("PRIVATE"));

        MockMultipartFile avatar = imageFile("avatar.png");
        MvcResult uploaded =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(avatar)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID assetId = UUID.fromString(body(uploaded).path("id").asText());
        setOriginalProfile(assetId, "source");
        mvc.perform(
                        put("/api/v3/account/avatar")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "spaceId",
                                                        OWNER_SPACE_ID.toString(),
                                                        "assetId",
                                                        assetId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarMedia.id").value(assetId.toString()))
                .andExpect(
                        jsonPath(
                                "$.avatarMedia.representations.original.url",
                                org.hamcrest.Matchers.containsString("profile=source")));

        MvcResult invitation =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/invitations", sharedSpaceId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                "other@example.com",
                                                                "role",
                                                                "MEMBER"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.token").isNotEmpty())
                        .andReturn();
        String token = body(invitation).path("token").asText();
        String otherToken = accessToken(login("other"));
        mvc.perform(
                        post("/api/v3/invitations/{token}/accept", token)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/members", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.username == 'other')].id")
                                .value(OTHER_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[1].email").doesNotExist())
                .andExpect(
                        jsonPath("$[?(@.username == 'owner')].avatarMedia.id")
                                .value(assetId.toString()));
        mvc.perform(
                        put(
                                        "/api/v3/spaces/{spaceId}/members/{accountId}/role",
                                        sharedSpaceId,
                                        OTHER_PUBLIC_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("role", "VIEWER"))))
                .andExpect(status().isNoContent());
        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/members/{accountId}",
                                        sharedSpaceId,
                                        OWNER_PUBLIC_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_LAST_OWNER"));
        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/members/{accountId}",
                                        sharedSpaceId,
                                        OTHER_PUBLIC_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", sharedSpaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void notificationsAndRemindersUseNativeJsonAndAccountIsolation() throws Exception {
        jdbc.update(
                """
                INSERT INTO notification(public_id,account_id,space_id,type,title,body,target_ref,created_at)
                VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                """,
                uuid(UUID.fromString("44444444-4444-4444-8444-444444444444")),
                101,
                11,
                "DIARY_REMINDER",
                "留下今天的回忆",
                "记录一个小瞬间",
                "{\"path\":\"/spaces\"}");
        String token = accessToken(login("owner"));
        mvc.perform(
                        get("/api/v3/notifications/push/public-key")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").doesNotExist());
        String endpoint = "https://push.example.com/subscription/owner";
        String subscription =
                json.writeValueAsString(
                        Map.of("endpoint", endpoint, "p256dh", "test-p256dh", "auth", "test-auth"));
        mvc.perform(
                        post("/api/v3/notifications/push/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(subscription))
                .andExpect(status().isNoContent());
        mvc.perform(
                        post("/api/v3/notifications/push/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(subscription))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM push_subscription WHERE account_id=101",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT LENGTH(endpoint_hash) FROM push_subscription WHERE account_id=101",
                                Integer.class))
                .isEqualTo(32);
        mvc.perform(
                        get("/api/v3/notifications/unread-count")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        MvcResult notifications =
                mvc.perform(
                                get("/api/v3/notifications")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].target.path").value("/spaces"))
                        .andReturn();
        UUID notificationId =
                UUID.fromString(body(notifications).path("items").get(0).path("id").asText());
        mvc.perform(
                        put("/api/v3/notifications/{notificationId}/read", notificationId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(
                        put("/api/v3/spaces/{spaceId}/reminders/DAILY", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("time", "08:30", "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DAILY"))
                .andExpect(jsonPath("$.schedule.time").value("08:30"));
        mvc.perform(
                        delete("/api/v3/notifications/push/subscriptions")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("endpoint", endpoint))))
                .andExpect(status().isNoContent());
    }

    @Test
    void offlineSyncPushIsIdempotentAndReportsVersionConflictsPerOperation() throws Exception {
        String token = accessToken(login("owner"));
        UUID operationId = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        Map<String, Object> payload =
                Map.of(
                        "title",
                        "Offline memory",
                        "date",
                        "2026-07-29",
                        "content",
                        "Saved offline",
                        "visibility",
                        "PRIVATE",
                        "locked",
                        false,
                        "tagIds",
                        List.of(),
                        "mediaIds",
                        List.of());
        String create =
                json.writeValueAsString(
                        Map.of(
                                "operations",
                                List.of(
                                        Map.of(
                                                "operationId",
                                                operationId,
                                                "entityType",
                                                "DIARY",
                                                "action",
                                                "CREATE",
                                                "entityId",
                                                diaryId,
                                                "payload",
                                                payload))));

        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/sync/push", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(create))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPLIED"))
                .andExpect(jsonPath("$[0].entityId").value(diaryId.toString()));
        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/sync/push", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(create))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPLIED"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM diary WHERE public_id=?",
                                Integer.class,
                                uuid(diaryId)))
                .isEqualTo(1);

        String conflict =
                json.writeValueAsString(
                        Map.of(
                                "operations",
                                List.of(
                                        Map.of(
                                                "operationId",
                                                UUID.randomUUID(),
                                                "entityType",
                                                "DIARY",
                                                "action",
                                                "UPDATE",
                                                "entityId",
                                                diaryId,
                                                "baseVersion",
                                                99,
                                                "payload",
                                                payload))));
        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/sync/push", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(conflict))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$[0].errorCode").value("DIARY_VERSION_MISMATCH"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/sync/pull", OWNER_SPACE_ID)
                                .queryParam("cursor", "0")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].entityId").value(diaryId.toString()));
    }

    @Test
    void permanentDiaryDeletionRequiresTrashAndSyncPullSignalsRetentionReset() throws Exception {
        String token = accessToken(login("owner"));
        UUID diaryId = createDiary(token, "Disposable memory", "2026-07-30");

        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/permanent",
                                        OWNER_SPACE_ID,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIARY_NOT_IN_TRASH"));

        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}",
                                        OWNER_SPACE_ID,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isNoContent());
        mvc.perform(
                        delete(
                                        "/api/v3/spaces/{spaceId}/diaries/{diaryId}/permanent",
                                        OWNER_SPACE_ID,
                                        diaryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM diary WHERE public_id=?",
                                Integer.class,
                                uuid(diaryId)))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM sync_change WHERE entity_public_id=? AND operation='DELETE'",
                                Integer.class,
                                uuid(diaryId)))
                .isEqualTo(2);

        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/sync/pull", OWNER_SPACE_ID)
                                .queryParam("cursor", "0")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].actorId").value(OWNER_PUBLIC_ID.toString()));
        jdbc.update(
                "INSERT INTO space_member(space_id,account_id,role,status) VALUES(11,202,'VIEWER','ACTIVE')");
        String otherToken = accessToken(login("other"));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/sync/pull", OWNER_SPACE_ID)
                                .queryParam("cursor", "0")
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes").isEmpty());

        jdbc.update(
                "INSERT INTO sync_retention(space_id,baseline_cursor) VALUES(11,50) ON DUPLICATE KEY UPDATE baseline_cursor=50");
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/sync/pull", OWNER_SPACE_ID)
                                .queryParam("cursor", "10")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetRequired").value(true))
                .andExpect(jsonPath("$.baselineCursor").value(50))
                .andExpect(jsonPath("$.changes").isEmpty());
    }

    @Test
    void portableArchiveRoundTripPreservesDiaryMediaAndRejectsDuplicateImport() throws Exception {
        String token = accessToken(login("owner"));
        MockMultipartFile image = imageFile("memory.png");
        MvcResult upload =
                mvc.perform(
                                multipart("/api/v3/spaces/{spaceId}/media", OWNER_SPACE_ID)
                                        .file(image)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isCreated())
                        .andReturn();
        String assetId = body(upload).path("id").asText();
        setOriginalProfile(UUID.fromString(assetId), "source");
        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "归档测试日记",
                                                        "diaryDate",
                                                        "2026-07-29",
                                                        "contentHtml",
                                                        "<p>一次完整的归档往返</p>",
                                                        "visibility",
                                                        "PRIVATE",
                                                        "locked",
                                                        false,
                                                        "tagIds",
                                                        List.of(),
                                                        "mediaIds",
                                                        List.of(assetId)))))
                .andExpect(status().isCreated());

        byte[] archive =
                mvc.perform(
                                get("/api/v3/spaces/{spaceId}/transfer/export", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(zipEntries(archive))
                .contains("manifest.json")
                .anyMatch(value -> value.startsWith("objects/"));

        MvcResult createdSpace =
                mvc.perform(
                                post("/api/v3/spaces")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Imported",
                                                                "defaultVisibility",
                                                                "PRIVATE"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID targetSpace = UUID.fromString(body(createdSpace).path("id").asText());
        MockMultipartFile archiveFile =
                new MockMultipartFile("archive", "export.zip", "application/zip", archive);
        mvc.perform(
                        multipart("/api/v3/spaces/{spaceId}/transfer/import", targetSpace)
                                .file(archiveFile)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedDiaries").value(1))
                .andExpect(jsonPath("$.importedMedia").value(1))
                .andExpect(jsonPath("$.skippedDiaries").value(0));
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/diaries", targetSpace)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("归档测试日记"))
                .andExpect(jsonPath("$.items[0].media.length()").value(1));
        mvc.perform(
                        multipart("/api/v3/spaces/{spaceId}/transfer/import", targetSpace)
                                .file(
                                        new MockMultipartFile(
                                                "archive",
                                                "export.zip",
                                                "application/zip",
                                                archive))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedDiaries").value(0))
                .andExpect(jsonPath("$.skippedDiaries").value(1));
    }

    @Test
    void archiveValidationRejectsTraversalAndUnsupportedManifestVersion() throws Exception {
        String token = accessToken(login("owner"));
        mvc.perform(
                        multipart("/api/v3/spaces/{spaceId}/transfer/import", OWNER_SPACE_ID)
                                .file(
                                        new MockMultipartFile(
                                                "archive",
                                                "bad.zip",
                                                "application/zip",
                                                zip(
                                                        Map.of(
                                                                "../outside.txt",
                                                                "bad",
                                                                "manifest.json",
                                                                "{}"))))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVE_PATH_INVALID"));
        mvc.perform(
                        multipart("/api/v3/spaces/{spaceId}/transfer/import", OWNER_SPACE_ID)
                                .file(
                                        new MockMultipartFile(
                                                "archive",
                                                "old.zip",
                                                "application/zip",
                                                zip(
                                                        Map.of(
                                                                "manifest.json",
                                                                "{\"version\":2,\"diaries\":[]}"))))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVE_VERSION_UNSUPPORTED"));
    }

    @Test
    void lockedExportsRequireStepUpAndBookFormatsAreValid() throws Exception {
        String token = accessToken(login("owner"));
        mvc.perform(
                        post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "锁定日记",
                                                        "diaryDate",
                                                        "2026-07-29",
                                                        "contentHtml",
                                                        "<p>private</p>",
                                                        "visibility",
                                                        "PRIVATE",
                                                        "locked",
                                                        true,
                                                        "tagIds",
                                                        List.of(),
                                                        "mediaIds",
                                                        List.of()))))
                .andExpect(status().isCreated());
        mvc.perform(
                        get("/api/v3/spaces/{spaceId}/transfer/export", OWNER_SPACE_ID)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        String stepToken =
                body(mvc.perform(
                                        post("/api/v3/auth/step-up")
                                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of("password", PASSWORD))))
                                .andExpect(status().isOk())
                                .andReturn())
                        .path("token")
                        .asText();
        byte[] pdf =
                mvc.perform(
                                get("/api/v3/spaces/{spaceId}/books", OWNER_SPACE_ID)
                                        .queryParam("format", "pdf")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .header("X-Step-Up-Token", stepToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(new String(pdf, 0, Math.min(4, pdf.length), StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        byte[] epub =
                mvc.perform(
                                get("/api/v3/spaces/{spaceId}/books", OWNER_SPACE_ID)
                                        .queryParam("format", "epub")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .header("X-Step-Up-Token", stepToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(zipEntries(epub))
                .contains("mimetype", "META-INF/container.xml", "OEBPS/book.xhtml");
    }

    @Test
    void backgroundJobsClaimAtomicallyRetryAndRecoverStaleWork() {
        jdbc.update(
                """
                INSERT INTO background_job(public_id,job_type,payload,max_attempts,available_at)
                VALUES(UUID_TO_BIN(UUID()),'AI_REPORT','{}',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                """);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        assertThat(
                        backgroundJobs.claim(
                                "worker:unsupported", now, List.of("MEDIA_PROCESS", "STORAGE_GC")))
                .isZero();
        jdbc.update("DELETE FROM background_job WHERE job_type='AI_REPORT'");

        jdbc.update(
                """
                INSERT INTO background_job(public_id,job_type,payload,max_attempts,available_at)
                VALUES(UUID_TO_BIN(UUID()),'STORAGE_GC','{}',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                """);
        assertThat(backgroundJobs.claim("worker:first", now, List.of("STORAGE_GC"))).isEqualTo(1);
        assertThat(backgroundJobs.claim("worker:second", now, List.of("STORAGE_GC"))).isZero();
        BackgroundJobMapper.JobRow first = backgroundJobs.findClaimed("worker:first");
        assertThat(first).isNotNull();
        assertThat(first.attemptCount()).isEqualTo(1);
        assertThat(
                        backgroundJobs.fail(
                                first.jobId(),
                                "worker:first",
                                false,
                                now.plusSeconds(15),
                                "retry",
                                now))
                .isEqualTo(1);
        jdbc.update(
                "UPDATE background_job SET available_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE job_id=?",
                first.jobId());
        assertThat(backgroundJobs.claim("worker:retry", now.plusSeconds(1), List.of("STORAGE_GC")))
                .isEqualTo(1);
        BackgroundJobMapper.JobRow retried = backgroundJobs.findClaimed("worker:retry");
        assertThat(retried.attemptCount()).isEqualTo(2);
        assertThat(backgroundJobs.fail(retried.jobId(), "worker:retry", true, now, "failed", now))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM background_job WHERE job_id=?",
                                String.class,
                                first.jobId()))
                .isEqualTo("FAILED");

        jdbc.update(
                """
                INSERT INTO background_job(public_id,job_type,payload,status,attempt_count,max_attempts,
                  available_at,claimed_at,claimed_by)
                VALUES(UUID_TO_BIN(UUID()),'STORAGE_GC','{}','RUNNING',1,3,UTC_TIMESTAMP(6),
                  DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 HOUR),'dead-worker')
                """);
        assertThat(backgroundJobs.recoverRetryable(now.minusMinutes(30), now)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM background_job WHERE status='PENDING'",
                                Integer.class))
                .isEqualTo(1);
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private List<String> zipEntries(byte[] bytes) throws Exception {
        List<String> entries = new java.util.ArrayList<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }
        return entries;
    }

    private MvcResult login(String username) throws Exception {
        return mvc.perform(
                        post("/api/v3/auth/login")
                                .header("X-Device-Name", "integration-test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        username,
                                                        "password",
                                                        PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
    }

    private UUID createSharedSpaceWithOther(String ownerToken, String otherToken) throws Exception {
        MvcResult created =
                mvc.perform(
                                post("/api/v3/spaces")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Shared permission space",
                                                                "defaultVisibility",
                                                                "SHARED"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        UUID spaceId = UUID.fromString(body(created).path("id").asText());
        MvcResult invitation =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/invitations", spaceId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                "other@example.com",
                                                                "role",
                                                                "MEMBER"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        mvc.perform(
                        post(
                                        "/api/v3/invitations/{token}/accept",
                                        body(invitation).path("token").asText())
                                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNoContent());
        return spaceId;
    }

    private String stepUp(String token) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v3/auth/step-up")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return body(result).path("token").asText();
    }

    private MockMultipartFile imageFile(String filename) throws Exception {
        return new MockMultipartFile("file", filename, "image/png", imageBytes());
    }

    private byte[] imageBytes() throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff6f61);
        image.setRGB(1, 0, 0x4f86c6);
        image.setRGB(0, 1, 0xf2c14e);
        image.setRGB(1, 1, 0x4c956c);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(javax.imageio.ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }

    private UUID createDiary(String token, String title, String date) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v3/spaces/{spaceId}/diaries", OWNER_SPACE_ID)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                title,
                                                                "diaryDate",
                                                                date,
                                                                "contentHtml",
                                                                "<p>Content</p>",
                                                                "visibility",
                                                                "PRIVATE",
                                                                "locked",
                                                                false,
                                                                "tagIds",
                                                                List.of(),
                                                                "mediaIds",
                                                                List.of()))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return UUID.fromString(body(result).path("id").asText());
    }

    private String accessToken(MvcResult result) throws Exception {
        return body(result).path("token").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void insertAccount(long id, UUID publicId, String username, String passwordHash) {
        insertAccount(id, publicId, username, passwordHash, "USER");
    }

    private void insertAccount(
            long id, UUID publicId, String username, String passwordHash, String role) {
        jdbc.update(
                """
                INSERT INTO account(account_id,public_id,username,password_hash,email,system_role,timezone,status)
                VALUES(?,?,?,?,?,?,'Asia/Shanghai','ACTIVE')
                """,
                id,
                uuid(publicId),
                username,
                passwordHash,
                username + "@example.com",
                role);
    }

    private void insertPersonalSpace(long id, UUID publicId, long accountId, String name) {
        jdbc.update(
                """
                INSERT INTO diary_space(space_id,public_id,name,type,created_by,personal_owner_id,
                  default_visibility,storage_quota_bytes)
                VALUES(?,?,?,'PERSONAL',?,?,'PRIVATE',5368709120)
                """,
                id,
                uuid(publicId),
                name,
                accountId,
                accountId);
        jdbc.update(
                "INSERT INTO space_member(space_id,account_id,role,status) VALUES(?,?,'OWNER','ACTIVE')",
                id,
                accountId);
        jdbc.update("INSERT INTO space_storage_usage(space_id,used_bytes) VALUES(?,0)", id);
    }

    private byte[] uuid(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private byte[] sha256(String value) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String legacyMediaUrl(UUID spaceId, UUID assetId, String variant) throws Exception {
        long expires = Instant.now().plusSeconds(1800).getEpochSecond();
        String normalized = variant.toUpperCase(java.util.Locale.ROOT);
        String payload = spaceId + "\n" + assetId + "\n" + normalized + "\n" + expires;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(
                new javax.crypto.spec.SecretKeySpec(
                        MEDIA_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature =
                java.util.HexFormat.of()
                        .formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return "/api/v3/public/media/"
                + spaceId
                + "/"
                + assetId
                + "/"
                + normalized.toLowerCase(java.util.Locale.ROOT)
                + "?expires="
                + expires
                + "&signature="
                + signature;
    }

    private Long setOriginalProfile(UUID assetId, String profile) {
        Long internalId =
                jdbc.queryForObject(
                        "SELECT asset_id FROM media_asset WHERE public_id=?",
                        Long.class,
                        uuid(assetId));
        jdbc.update(
                "UPDATE media_variant SET profile=? WHERE asset_id=? AND variant_type='ORIGINAL'",
                profile,
                internalId);
        return internalId;
    }
}

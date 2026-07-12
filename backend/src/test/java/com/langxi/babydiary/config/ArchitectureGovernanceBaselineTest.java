package com.langxi.babydiary.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureGovernanceBaselineTest {

    @Test
    void cacheNamesAreCentralizedAndUsedByServices() throws Exception {
        String cacheNames = read("src/main/java/com/langxi/babydiary/common/CacheNames.java");
        String serviceSources = readAllJavaSources(Paths.get("src/main/java/com/langxi/babydiary/service"));
        String config = read("src/main/java/com/langxi/babydiary/config/CacheConfig.java");

        assertThat(cacheNames).contains("public final class CacheNames");
        assertThat(cacheNames).contains("DIARY_PAGE");
        assertThat(cacheNames).contains("PHOTOS");
        assertThat(serviceSources).contains("CacheNames.DIARY_PAGE");
        assertThat(serviceSources).contains("CacheNames.PHOTOS");
        assertThat(serviceSources).doesNotContain("cacheNames = \"diaryPage\"");
        assertThat(serviceSources).doesNotContain("cacheNames = \"photos\"");
        assertThat(config).contains("CacheNames.ttlByCacheName()");
    }

    @Test
    void dateQueriesUseSargableDateRangesAndAvoidRandomOrdering() throws Exception {
        String diaryXml = read("src/main/resources/mapper/DiaryMapper.xml");
        String albumXml = read("src/main/resources/mapper/AlbumMapper.xml");
        String photoXml = read("src/main/resources/mapper/PhotoMapper.xml");

        assertThat(diaryXml).doesNotContain("YEAR(date)");
        assertThat(diaryXml).doesNotContain("MONTH(date)");
        assertThat(diaryXml).doesNotContain("YEAR(d.date)");
        assertThat(diaryXml).doesNotContain("MONTH(d.date)");
        assertThat(diaryXml).contains("date &gt;= #{startDate}");
        assertThat(diaryXml).contains("date &lt;= #{endDate}");
        assertThat(albumXml).doesNotContain("ORDER BY RAND()");
        assertThat(photoXml).doesNotContain("ORDER BY RAND()");
    }

    @Test
    void slowRequestLoggingIsRegisteredAndConfigurable() throws Exception {
        String interceptor = read("src/main/java/com/langxi/babydiary/config/SlowRequestLoggingInterceptor.java");
        String webConfig = read("src/main/java/com/langxi/babydiary/config/WebConfig.java");
        String prodConfig = read("../config/application-prod.yml");

        assertThat(interceptor).contains("HandlerInterceptor");
        assertThat(interceptor).contains("app.http.slow-request-threshold-ms");
        assertThat(webConfig).contains("addInterceptors");
        assertThat(webConfig).contains("slowRequestLoggingInterceptor");
        assertThat(prodConfig).contains("slow-request-threshold-ms");
    }

    @Test
    void explainScriptCoversKeyQueries() throws Exception {
        String script = read("../scripts/explain-key-queries.sh");
        String migration = read("src/main/resources/db/migration/V7__architecture_governance_indexes.sql");

        assertThat(script).contains("diary-list");
        assertThat(script).contains("timeline");
        assertThat(script).contains("album-list");
        assertThat(script).contains("album-detail");
        assertThat(script).contains("ai-report-history");
        assertThat(migration).contains("idx_album_photo_album_sort");
        assertThat(migration).contains("idx_ai_report_user_created_report");
    }

    @Test
    void sensitiveConfigurationHasNoSourceControlledDefaults() throws Exception {
        String applicationConfig = read("src/main/resources/application.yml");
        String productionConfig = read("../config/application-prod.yml");
        String jwtProvider = read("src/main/java/com/langxi/babydiary/security/JwtTokenProvider.java");
        String aiConfigCrypto = read("src/main/java/com/langxi/babydiary/service/AiConfigCrypto.java");
        String invitationCodeCrypto = read("src/main/java/com/langxi/babydiary/service/InvitationCodeCrypto.java");

        assertThat(applicationConfig).contains("password: ${DB_PASSWORD}");
        assertThat(applicationConfig).contains("bootstrap-code: ${INVITATION_CODE:}");
        assertThat(applicationConfig).contains("encryption-key: ${INVITATION_CODE_ENCRYPTION_KEY}");
        assertThat(applicationConfig).contains("secret: ${JWT_SECRET}");
        assertThat(applicationConfig).contains("encryption-key: ${AI_CONFIG_ENCRYPTION_KEY}");
        assertThat(applicationConfig).doesNotContain("${DB_PASSWORD:");
        assertThat(applicationConfig).doesNotContain("${JWT_SECRET:");
        assertThat(applicationConfig).doesNotContain("${INVITATION_CODE_ENCRYPTION_KEY:");
        assertThat(applicationConfig).doesNotContain("${AI_CONFIG_ENCRYPTION_KEY:");
        assertThat(productionConfig).doesNotContain("/usr/local/");
        assertThat(jwtProvider).contains("@Value(\"${jwt.secret}\")");
        assertThat(aiConfigCrypto).contains("@Value(\"${ai.config.encryption-key}\")");
        assertThat(invitationCodeCrypto).contains("@Value(\"${app.invitation.encryption-key}\")");
    }

    private String readAllJavaSources(Path root) throws Exception {
        StringBuilder content = new StringBuilder();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            content.append(read(path.toString())).append('\n');
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to read " + path, e);
                        }
                    });
        }
        return content.toString();
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}

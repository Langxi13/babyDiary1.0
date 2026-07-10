package com.langxi.babydiary.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBoot35BaselineTest {

    @Test
    void backendUsesSpringBoot35AndBoot3CompatibleDependencies() throws Exception {
        String pom = read("pom.xml");

        assertThat(pom).contains("<spring-boot.version>3.5.16</spring-boot.version>");
        assertThat(pom).contains("<artifactId>spring-boot-starter-actuator</artifactId>");
        assertThat(pom).contains("<artifactId>mybatis-spring-boot-starter</artifactId>");
        assertThat(pom).contains("<version>3.0.5</version>");
        assertThat(pom).contains("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>");
        assertThat(pom).contains("<version>2.8.17</version>");
        assertThat(pom).doesNotContain("<spring-boot.version>2.");
        assertThat(pom).doesNotContain("<artifactId>springdoc-openapi-ui</artifactId>");
    }

    @Test
    void securityConfigurationUsesSpringSecurity6Style() throws Exception {
        String source = read("src/main/java/com/langxi/babydiary/config/SecurityConfig.java");

        assertThat(source).contains("SecurityFilterChain");
        assertThat(source).contains("@EnableMethodSecurity");
        assertThat(source).contains("requestMatchers(");
        assertThat(source).contains("\"/actuator/health\"");
        assertThat(source).doesNotContain("WebSecurityConfigurerAdapter");
        assertThat(source).doesNotContain("@EnableGlobalMethodSecurity");
        assertThat(source).doesNotContain("antMatchers(");
        assertThat(source).doesNotContain("authorizeRequests()");
    }

    @Test
    void applicationCodeUsesJakartaServletAndValidationPackages() throws Exception {
        String allJavaSources = readAllJavaSources(Paths.get("src/main/java"));

        assertThat(allJavaSources).contains("jakarta.servlet");
        assertThat(allJavaSources).contains("jakarta.validation");
        assertThat(allJavaSources).contains("jakarta.annotation.PostConstruct");
        assertThat(allJavaSources).doesNotContain("javax.servlet");
        assertThat(allJavaSources).doesNotContain("javax.validation");
        assertThat(allJavaSources).doesNotContain("javax.annotation.PostConstruct");
    }

    @Test
    void productionConfigOnlyExposesActuatorHealth() throws Exception {
        String prodConfig = read("../config/application-prod.yml");

        assertThat(prodConfig).contains("management:");
        assertThat(prodConfig).contains("exposure:");
        assertThat(prodConfig).contains("include: health");
        assertThat(prodConfig).contains("show-details: never");
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

package com.langxi.babydiary.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.api.ApiContract;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, AuthenticationFilter authenticationFilter, ObjectMapper objectMapper)
            throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                ApiContract.ROOT + "/auth/login",
                                                ApiContract.ROOT + "/auth/register",
                                                ApiContract.ROOT + "/auth/refresh",
                                                ApiContract.ROOT + "/auth/logout",
                                                ApiContract.ROOT + "/auth/email/confirm",
                                                ApiContract.ROOT + "/auth/password/reset-request",
                                                ApiContract.ROOT + "/auth/password/reset",
                                                ApiContract.ROOT + "/auth/password/recover",
                                                ApiContract.ROOT + "/public/media/**",
                                                ApiContract.ROOT + "/client/bootstrap",
                                                ApiContract.ROOT + "/public/**",
                                                "/actuator/health",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions.authenticationEntryPoint(
                                        (request, response, failure) -> {
                                            ProblemDetail detail =
                                                    ProblemDetail.forStatusAndDetail(
                                                            HttpStatus.UNAUTHORIZED, "请先登录");
                                            detail.setTitle("Unauthorized");
                                            detail.setType(
                                                    URI.create(
                                                            "urn:baby-diary:problem:authentication-required"));
                                            detail.setProperty("code", "AUTHENTICATION_REQUIRED");
                                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                            response.setContentType(
                                                    MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                                            objectMapper.writeValue(
                                                    response.getOutputStream(), detail);
                                        }))
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                    String webOrigins,
            @Value("${cors.native-origins:https://localhost,capacitor://localhost}")
                    String nativeOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream((webOrigins + "," + nativeOrigins).split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList());
        configuration.setAllowedMethods(
                List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "If-Match",
                        "X-Device-Name",
                        "X-Step-Up-Token",
                        "X-Client-Platform",
                        "X-Client-Version-Code"));
        configuration.setExposedHeaders(
                List.of(
                        "ETag",
                        "Location",
                        "X-Request-Id",
                        "Accept-Ranges",
                        "Content-Range",
                        "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiContract.ROOT + "/**", configuration);
        return source;
    }
}

package com.langxi.babydiary.v3.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class V3SecurityConfig {

    @Bean
    SecurityFilterChain v3SecurityFilterChain(HttpSecurity http, V3AuthenticationFilter authenticationFilter,
                                              ObjectMapper objectMapper) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v3/auth/login",
                                "/api/v3/auth/register",
                                "/api/v3/auth/refresh",
                                "/api/v3/auth/logout",
                                "/api/v3/auth/email/confirm",
                                "/api/v3/auth/password/reset-request",
                                "/api/v3/auth/password/reset",
                                "/api/v3/auth/password/recover",
                                "/api/v3/public/media/**",
                                "/api/v3/client/bootstrap",
                                "/api/v3/public/**",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, failure) -> {
                    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "请先登录");
                    detail.setTitle("Unauthorized");
                    detail.setType(URI.create("urn:baby-diary:problem:authentication-required"));
                    detail.setProperty("code", "AUTHENTICATION_REQUIRED");
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), detail);
                }))
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    PasswordEncoder v3PasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource v3CorsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String webOrigins,
            @Value("${cors.native-origins:https://localhost,capacitor://localhost}") String nativeOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream((webOrigins + "," + nativeOrigins).split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList());
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "X-Device-Name",
                "X-Step-Up-Token"));
        configuration.setExposedHeaders(List.of("ETag", "Location", "X-Request-Id", "Accept-Ranges",
                "Content-Range", "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v3/**", configuration);
        return source;
    }
}

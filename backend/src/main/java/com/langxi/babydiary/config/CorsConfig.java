package com.langxi.babydiary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${cors.native-origins:}")
    private String nativeOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        Stream<String> configuredOrigins = Stream.concat(
                Arrays.stream(allowedOrigins.split(",")),
                Arrays.stream(nativeOrigins.split(","))
        ).map(String::trim).filter(origin -> !origin.isEmpty());
        String[] origins = configuredOrigins.distinct().toArray(String[]::new);

        if (Arrays.stream(origins).anyMatch("*"::equals)) {
            throw new IllegalStateException("cors.allowed-origins must list explicit origins when credentials are enabled");
        }
        Arrays.stream(origins).forEach(config::addAllowedOrigin);
        
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition", "ETag"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

package com.langxi.babydiary.platform.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public final class ProductVersion {
    private final String value;

    public ProductVersion() {
        Properties properties = new Properties();
        try (InputStream input =
                ProductVersion.class
                        .getClassLoader()
                        .getResourceAsStream("release-version.properties")) {
            if (input == null)
                throw new IllegalStateException("release-version.properties is missing");
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read release-version.properties", exception);
        }
        value = required(properties.getProperty("PRODUCT_VERSION"));
    }

    public String value() {
        return value;
    }

    private String required(String configured) {
        String normalized = configured == null ? "" : configured.trim();
        if (!normalized.matches("[0-9]+(\\.[0-9]+){1,3}([.-][A-Za-z0-9]+)*")) {
            throw new IllegalStateException("PRODUCT_VERSION is invalid");
        }
        return normalized;
    }
}

package com.langxi.babydiary.v3.ai.application;

public record AiRuntimeConfig(String baseUrl, String apiKey, String model, int timeoutSeconds) {
}

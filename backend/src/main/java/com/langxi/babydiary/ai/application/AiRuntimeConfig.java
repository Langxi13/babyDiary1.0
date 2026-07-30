package com.langxi.babydiary.ai.application;

public record AiRuntimeConfig(String baseUrl, String apiKey, String model, int timeoutSeconds) {
}

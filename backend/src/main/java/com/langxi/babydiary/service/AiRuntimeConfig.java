package com.langxi.babydiary.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiRuntimeConfig {
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer timeoutSeconds;
}

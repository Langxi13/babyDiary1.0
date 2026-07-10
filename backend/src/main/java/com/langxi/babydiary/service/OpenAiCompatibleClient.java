package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpenAiCompatibleClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean manageTimeout;

    public OpenAiCompatibleClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.manageTimeout = true;
    }

    public OpenAiCompatibleClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.manageTimeout = false;
    }

    public String generate(AiRuntimeConfig config, List<AiChatMessage> messages) {
        if (manageTimeout) {
            configureTimeout(config.getTimeoutSeconds());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModel());
        body.put("messages", messages.stream().map(message -> {
            Map<String, String> item = new HashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            return item;
        }).collect(Collectors.toList()));
        body.put("temperature", 0.7);

        try {
            String response = restTemplate.postForObject(chatCompletionUrl(config.getBaseUrl()), new HttpEntity<>(body, headers), String.class);
            JsonNode content = objectMapper.readTree(response).path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI响应为空");
            }
            return content.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("AI接口请求失败: baseUrl={}, reason={}", config.getBaseUrl(), failureReason(e));
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI接口请求失败，请检查配置或稍后重试");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI响应解析失败");
        }
    }

    public List<String> listModels(AiRuntimeConfig config) {
        if (manageTimeout) {
            configureTimeout(config.getTimeoutSeconds());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getApiKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(modelsUrl(config.getBaseUrl()), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (!data.isArray()) {
                throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI模型列表响应格式无效");
            }
            List<String> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText("").trim();
                if (!id.isEmpty()) {
                    models.add(id);
                }
            }
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("AI模型列表请求失败: baseUrl={}, reason={}", config.getBaseUrl(), failureReason(e));
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI模型列表请求失败，请检查配置或稍后重试");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI模型列表解析失败");
        }
    }

    private String chatCompletionUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl) + "/chat/completions";
    }

    private String modelsUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl) + "/models";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String failureReason(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        return exception.getClass().getSimpleName();
    }

    private void configureTimeout(Integer timeoutSeconds) {
        int millis = Math.max(timeoutSeconds == null ? 30 : timeoutSeconds, 1) * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        restTemplate.setRequestFactory(factory);
    }
}

package com.langxi.babydiary.v3.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class V3AiClient {
    private final ObjectMapper json;

    public V3AiClient(ObjectMapper json) {
        this.json = json;
    }

    public String generate(AiRuntimeConfig config, List<Message> messages) {
        RestTemplate rest = rest(config.timeoutSeconds());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put("temperature", 0.7);
        try {
            String raw = rest.postForObject(url(config.baseUrl(), "/chat/completions"),
                    new HttpEntity<>(body, headers), String.class);
            JsonNode content = json.readTree(raw).path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) throw failed("AI 响应为空");
            return content.asText();
        } catch (V3Exception exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_REQUEST_FAILED", "AI 接口请求失败");
        } catch (Exception exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID", "AI 响应格式无效");
        }
    }

    public List<String> listModels(AiRuntimeConfig config) {
        RestTemplate rest = rest(config.timeoutSeconds());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.apiKey());
        try {
            ResponseEntity<String> response = rest.exchange(url(config.baseUrl(), "/models"), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            JsonNode data = json.readTree(response.getBody()).path("data");
            if (!data.isArray()) throw failed("模型列表格式无效");
            List<String> result = new ArrayList<>();
            data.forEach(item -> { if (item.path("id").isTextual() && !item.path("id").asText().isBlank()) result.add(item.path("id").asText()); });
            return result;
        } catch (V3Exception exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_MODELS_FAILED", "模型列表请求失败");
        } catch (Exception exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_MODELS_INVALID", "模型列表格式无效");
        }
    }

    private RestTemplate rest(int seconds) {
        int millis = Math.max(5, Math.min(seconds, 300)) * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        return new RestTemplate(factory);
    }

    private String url(String base, String suffix) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + suffix : base + suffix;
    }

    private V3Exception failed(String message) {
        return new V3Exception(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_REQUEST_FAILED", message);
    }

    public record Message(String role, String content) {
    }
}

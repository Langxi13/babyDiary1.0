package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OpenAiCompatibleClientTest {

    @Test
    void postsChatCompletionRequestAndReadsAssistantContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(restTemplate);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"# 周报\"}}]}", MediaType.APPLICATION_JSON));

        String content = client.generate(
                new AiRuntimeConfig("https://api.example.com/v1", "sk-test", "test-model", 10),
                Arrays.asList(new AiChatMessage("system", "你是助手"))
        );

        assertThat(content).isEqualTo("# 周报");
        server.verify();
    }

    @Test
    void listsModelIdsFromOpenAiCompatibleModelsEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(restTemplate);

        server.expect(requestTo("https://api.example.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"gpt-4o-mini\"},{\"id\":\"gpt-4.1-mini\"}]}", MediaType.APPLICATION_JSON));

        List<String> models = client.listModels(new AiRuntimeConfig("https://api.example.com/v1", "sk-test", "", 10));

        assertThat(models).containsExactly("gpt-4o-mini", "gpt-4.1-mini");
        server.verify();
    }

    @Test
    void downstreamErrorDoesNotExposeClientInternals() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(restTemplate);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("upstream-secret-detail"));

        assertThatThrownBy(() -> client.generate(
                new AiRuntimeConfig("https://api.example.com/v1", "sk-test", "test-model", 10),
                Arrays.asList(new AiChatMessage("user", "test"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.AI_REQUEST_FAILED.getCode());
                    assertThat(exception.getMessage()).doesNotContain("upstream-secret-detail", "502");
                });
        server.verify();
    }
}

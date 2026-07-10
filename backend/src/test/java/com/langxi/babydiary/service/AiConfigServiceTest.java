package com.langxi.babydiary.service;

import com.langxi.babydiary.dto.AiConfigDTO;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AiConfigServiceTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    @Mock
    private AiConfigCrypto aiConfigCrypto;

    @Mock
    private OpenAiCompatibleClient aiClient;

    @InjectMocks
    private AiConfigService aiConfigService;

    @Test
    void saveConfigRejectsNonHttpBaseUrls() {
        AiConfigDTO dto = new AiConfigDTO();
        dto.setEnabled(true);
        dto.setBaseUrl("file:///etc/passwd");
        dto.setModel("test-model");
        dto.setApiKey("sk-test");

        assertThatThrownBy(() -> aiConfigService.saveConfig(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP");
    }

    @Test
    void saveConfigRejectsBaseUrlsWithCredentialsOrQueryParameters() {
        AiConfigDTO dto = new AiConfigDTO();
        dto.setEnabled(true);
        dto.setBaseUrl("https://user:pass@example.com/v1?redirect=internal");
        dto.setModel("test-model");
        dto.setApiKey("sk-test");

        assertThatThrownBy(() -> aiConfigService.saveConfig(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Base URL");
    }
}

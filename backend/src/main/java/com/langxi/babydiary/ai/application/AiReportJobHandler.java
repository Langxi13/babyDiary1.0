package com.langxi.babydiary.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BackgroundJobHandler;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AiReportJobHandler implements BackgroundJobHandler {
    private final AiReportService reports;
    private final ObjectMapper json;

    public AiReportJobHandler(AiReportService reports, ObjectMapper json) {
        this.reports = reports;
        this.json = json;
    }

    @Override
    public String type() {
        return "AI_REPORT";
    }

    @Override
    public JsonNode handle(JsonNode payload) {
        UUID spaceId = UUID.fromString(payload.path("spaceId").asText());
        long accountId = payload.path("accountId").asLong(0);
        String type = payload.path("type").asText();
        String period = payload.path("period").asText();
        if (accountId <= 0 || type.isBlank() || period.isBlank()) {
            throw new IllegalArgumentException("AI report job payload is invalid");
        }
        try {
            AiReportService.ReportView report =
                    reports.findExisting(spaceId, accountId, type, period)
                            .orElseGet(() -> reports.generate(spaceId, accountId, type, period));
            return json.valueToTree(Map.of("reportId", report.id().toString()));
        } catch (ApiException exception) {
            if ("AI_REPORT_NO_DIARIES".equals(exception.code())) {
                return json.createObjectNode().put("skipped", "NO_DIARIES");
            }
            throw exception;
        }
    }
}

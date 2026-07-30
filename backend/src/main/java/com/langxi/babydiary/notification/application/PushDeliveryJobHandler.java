package com.langxi.babydiary.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.BackgroundJobHandler;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PushDeliveryJobHandler implements BackgroundJobHandler {
    private final PushSubscriptionRepository subscriptions;
    private final PushGateway gateway;
    private final ObjectMapper json;

    public PushDeliveryJobHandler(
            PushSubscriptionRepository subscriptions, PushGateway gateway, ObjectMapper json) {
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.json = json;
    }

    @Override
    public String type() {
        return "PUSH_DELIVERY";
    }

    @Override
    public JsonNode handle(JsonNode payload) throws Exception {
        if (!gateway.configured()) {
            return json.createObjectNode().put("configured", false);
        }
        long accountId = payload.path("accountId").asLong(0);
        if (accountId <= 0) throw new IllegalArgumentException("Push accountId is invalid");

        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", payload.path("title").asText("Baby Diary"));
        if (payload.hasNonNull("body")) body.put("body", payload.path("body").asText());
        if (payload.hasNonNull("targetPath")) {
            body.put("targetPath", payload.path("targetPath").asText());
        }
        String serialized = json.writeValueAsString(body);
        int sent = 0;
        int revoked = 0;
        int transientFailures = 0;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (PushSubscriptionRepository.Subscription subscription :
                subscriptions.findActive(accountId)) {
            try {
                int status = gateway.send(subscription, serialized);
                if (status >= 200 && status < 300) {
                    subscriptions.markSuccess(subscription.internalId(), now);
                    sent++;
                } else if (status == 404 || status == 410) {
                    subscriptions.revokeById(subscription.internalId(), now);
                    revoked++;
                } else {
                    transientFailures++;
                }
            } catch (Exception exception) {
                transientFailures++;
            }
        }
        if (transientFailures > 0) {
            throw new IllegalStateException(
                    transientFailures + " Web Push deliveries require retry");
        }
        return json.valueToTree(Map.of("sent", sent, "revoked", revoked));
    }
}

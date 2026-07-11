package com.langxi.babydiary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.entity.PushSubscription;
import com.langxi.babydiary.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class WebPushService {
    private final NotificationMapper mapper;
    private final ObjectMapper objectMapper;

    @Value("${app.push.vapid-public-key:}")
    private String publicKey;

    @Value("${app.push.vapid-private-key:}")
    private String privateKey;

    @Value("${app.push.subject:mailto:admin@example.com}")
    private String subject;

    public WebPushService(NotificationMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }

    public String publicKey() {
        return isConfigured() ? publicKey : null;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(PushNotificationEvent event) {
        if (!isConfigured()) return;
        String payload = payload(event);
        for (PushSubscription subscription : mapper.findActiveSubscriptions(event.userId())) {
            try {
                PushService service = new PushService(publicKey, privateKey, subject);
                HttpResponse response = service.send(new Notification(
                        subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuthSecret(), payload));
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) mapper.markPushSuccess(subscription.getSubscriptionId());
                else if (status == 404 || status == 410) mapper.revokeSubscriptionById(subscription.getSubscriptionId());
                else log.warn("Web Push发送失败: subscriptionId={}, status={}", subscription.getSubscriptionId(), status);
            } catch (Exception exception) {
                log.warn("Web Push发送异常: subscriptionId={}, reason={}",
                        subscription.getSubscriptionId(), exception.getMessage());
                if (Thread.currentThread().isInterrupted()) return;
            }
        }
    }

    private String payload(PushNotificationEvent event) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("title", event.title());
        payload.put("body", event.body());
        payload.put("targetPath", event.targetPath());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"title\":\"Baby Diary\"}";
        }
    }
}

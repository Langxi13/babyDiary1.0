package com.langxi.babydiary.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.OutboxEventHandler;
import com.langxi.babydiary.platform.application.OutboxEventRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DiaryOutboxEventHandler implements OutboxEventHandler {
    private static final Set<String> TYPES =
            Set.of("DIARY_CREATED", "DIARY_UPDATED", "DIARY_DELETED", "DIARY_RESTORED");

    private final NotificationRepository notifications;
    private final NotificationDeliveryService delivery;
    private final ObjectMapper json;

    public DiaryOutboxEventHandler(
            NotificationRepository notifications,
            NotificationDeliveryService delivery,
            ObjectMapper json) {
        this.notifications = notifications;
        this.delivery = delivery;
        this.json = json;
    }

    @Override
    public Set<String> eventTypes() {
        return TYPES;
    }

    @Override
    public JsonNode handle(OutboxEventRepository.Event event) {
        if (!"SHARED".equals(event.payload().path("visibility").asText())
                || event.spaceInternalId() == null
                || event.spaceId() == null
                || event.aggregateId() == null) {
            return json.createObjectNode().put("skipped", true);
        }
        String path = "/spaces/" + event.spaceId() + "/diaries/" + event.aggregateId();
        Message message = message(event.eventType());
        int delivered = 0;
        for (long recipient :
                notifications.findActiveSpaceMemberIds(event.spaceInternalId(), event.actorId())) {
            if (delivery.notifyUser(
                    recipient,
                    event.spaceInternalId(),
                    "DIARY_ACTIVITY",
                    message.title(),
                    message.body(),
                    path,
                    "outbox:" + event.id() + ":" + recipient)) {
                delivered++;
            }
        }
        return json.valueToTree(Map.of("notifications", delivered));
    }

    private Message message(String type) {
        return switch (type) {
            case "DIARY_CREATED" -> new Message("共同日记有新记录", "空间成员新增了一篇共同日记");
            case "DIARY_UPDATED" -> new Message("共同日记已更新", "空间成员更新了一篇共同日记");
            case "DIARY_DELETED" -> new Message("共同日记已移入回收站", "一篇共同日记已被移入回收站");
            case "DIARY_RESTORED" -> new Message("共同日记已恢复", "一篇共同日记已从回收站恢复");
            default -> new Message("共同日记有新动态", "共同空间中有一条新动态");
        };
    }

    private record Message(String title, String body) {}
}

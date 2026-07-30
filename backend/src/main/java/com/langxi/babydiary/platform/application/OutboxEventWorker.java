package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxEventWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventWorker.class);

    private final OutboxEventRepository events;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Map<String, OutboxEventHandler> handlers;
    private final boolean enabled;
    private final String workerId;
    private final int staleMinutes;

    public OutboxEventWorker(
            OutboxEventRepository events,
            ObjectMapper json,
            TransactionTemplate transactions,
            List<OutboxEventHandler> handlers,
            @Value("${app.outbox.enabled:true}") boolean enabled,
            @Value("${app.jobs.worker-id:single-node}") String workerId,
            @Value("${app.outbox.stale-after-minutes:10}") int staleMinutes) {
        this.events = events;
        this.json = json;
        this.transactions = transactions;
        this.enabled = enabled;
        this.workerId = truncate(workerId == null ? "single-node" : workerId, 40);
        this.staleMinutes = Math.max(2, staleMinutes);
        Map<String, OutboxEventHandler> registered = new LinkedHashMap<>();
        for (OutboxEventHandler handler : handlers) {
            for (String type : handler.eventTypes()) {
                if (registered.putIfAbsent(type, handler) != null) {
                    throw new IllegalStateException("Duplicate outbox event handler: " + type);
                }
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}")
    public void poll() {
        if (!enabled) return;
        String claim = workerId + ":" + UUID.randomUUID();
        OutboxEventRepository.Event event =
                transactions.execute(
                        status ->
                                events.claim(claim, now()) == 1 ? events.findClaimed(claim) : null);
        if (event == null) return;
        execute(event, claim);
    }

    @Scheduled(fixedDelayString = "${app.outbox.recovery-delay-ms:300000}")
    public void recoverStale() {
        if (!enabled) return;
        LocalDateTime now = now();
        LocalDateTime staleBefore = now.minusMinutes(staleMinutes);
        transactions.executeWithoutResult(
                status -> {
                    int retried = events.recoverRetryable(staleBefore, now);
                    int failed = events.failExhausted(staleBefore, now);
                    if (retried + failed > 0) {
                        log.warn("Recovered {} stale outbox events; {} exhausted", retried, failed);
                    }
                });
    }

    private void execute(OutboxEventRepository.Event event, String claim) {
        try {
            OutboxEventHandler handler = handlers.get(event.eventType());
            JsonNode result =
                    handler == null
                            ? json.createObjectNode().put("ignored", true)
                            : handler.handle(event);
            if (result == null) result = json.createObjectNode();
            transactions.executeWithoutResult(
                    status -> {
                        if (events.succeed(event.eventId(), claim, now()) != 1) {
                            throw new IllegalStateException("Outbox event claim was lost");
                        }
                    });
        } catch (Exception exception) {
            fail(event, claim, exception);
        }
    }

    private void fail(OutboxEventRepository.Event event, String claim, Exception exception) {
        boolean terminal = event.attemptCount() >= event.maxAttempts();
        long delaySeconds = Math.min(1800, 10L * (1L << Math.min(event.attemptCount() - 1, 7)));
        LocalDateTime now = now();
        String error =
                truncate(
                        exception.getClass().getSimpleName()
                                + ": "
                                + (exception.getMessage() == null
                                        ? "未知错误"
                                        : exception.getMessage()),
                        2000);
        transactions.executeWithoutResult(
                status ->
                        events.fail(
                                event.eventId(),
                                claim,
                                terminal,
                                now.plusSeconds(delaySeconds),
                                error,
                                now));
        log.warn(
                "Outbox event {} ({}) failed on attempt {}/{}",
                event.id(),
                event.eventType(),
                event.attemptCount(),
                event.maxAttempts(),
                exception);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String truncate(String value, int maxLength) {
        return value.substring(0, Math.min(value.length(), maxLength));
    }
}

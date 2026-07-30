package com.langxi.babydiary.reminder.application;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryWorker.class);

    private final ReminderRepository reminders;
    private final ReminderDeliveryService delivery;
    private final boolean enabled;

    public ReminderDeliveryWorker(
            ReminderRepository reminders,
            ReminderDeliveryService delivery,
            @Value("${app.reminders.enabled:true}") boolean enabled) {
        this.reminders = reminders;
        this.delivery = delivery;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.reminders.delivery-delay-ms:60000}")
    public void deliverDue() {
        if (!enabled) return;
        for (ReminderRepository.DueReminder reminder :
                reminders.findDue(LocalDateTime.now(ZoneOffset.UTC), 25)) {
            try {
                delivery.deliver(reminder);
            } catch (DateTimeException | IllegalArgumentException exception) {
                delivery.disable(reminder);
                log.warn("Disabled invalid reminder {}: {}", reminder.id(), exception.getMessage());
            } catch (RuntimeException exception) {
                log.warn("Reminder delivery {} failed", reminder.id(), exception);
            }
        }
    }
}

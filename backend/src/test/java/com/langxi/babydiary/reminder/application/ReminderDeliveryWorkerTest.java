package com.langxi.babydiary.reminder.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReminderDeliveryWorkerTest {
    @Test
    void disablesReminderWithInvalidDateTimeSchedule() {
        ReminderRepository reminders = mock(ReminderRepository.class);
        ReminderDeliveryService delivery = mock(ReminderDeliveryService.class);
        ReminderRepository.DueReminder reminder =
                new ReminderRepository.DueReminder(
                        1L,
                        UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                        2L,
                        3L,
                        UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                        "测试空间",
                        "WEEKLY",
                        new ObjectMapper().createObjectNode().put("time", "99:99"),
                        LocalDateTime.of(2026, 7, 30, 0, 0));
        when(reminders.findDue(any(LocalDateTime.class), eq(25))).thenReturn(List.of(reminder));
        doThrow(new DateTimeException("invalid schedule")).when(delivery).deliver(reminder);

        new ReminderDeliveryWorker(reminders, delivery, true).deliverDue();

        verify(delivery).disable(reminder);
    }
}

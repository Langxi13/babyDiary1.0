package com.langxi.babydiary.reminder.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.reminder.application.ReminderRepository;
import com.langxi.babydiary.reminder.application.ReminderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/reminders")
public class ReminderController {
    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping
    public List<ReminderRepository.Row> list(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId) {
        return reminders.list(spaceId, principal.accountId());
    }

    @PutMapping("/{type}")
    public ReminderRepository.Row save(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable String type,
            @Valid @RequestBody ReminderRequest request) {
        return reminders.save(
                spaceId,
                principal.accountId(),
                type,
                request.time(),
                request.dayOfWeek(),
                request.enabled());
    }

    public record ReminderRequest(
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$") String time,
            Integer dayOfWeek,
            boolean enabled) {}
}

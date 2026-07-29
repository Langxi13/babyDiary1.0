package com.langxi.babydiary.v3.notification.api;

import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.notification.application.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.langxi.babydiary.v3.notification.application.PushSubscriptionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v3/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final PushSubscriptionService push;

    public NotificationController(NotificationService notifications, PushSubscriptionService push) {
        this.notifications = notifications;
        this.push = push;
    }

    @GetMapping
    public NotificationService.Page list(@AuthenticationPrincipal V3Principal principal,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return notifications.list(principal.accountId(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread(@AuthenticationPrincipal V3Principal principal) {
        return new UnreadCount(notifications.unread(principal.accountId()));
    }

    @PutMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal V3Principal principal, @PathVariable UUID notificationId) {
        notifications.markRead(principal.accountId(), notificationId);
    }

    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal V3Principal principal) {
        notifications.markAllRead(principal.accountId());
    }

    @GetMapping("/push/public-key") public PushKey publicKey(){return new PushKey(push.publicKey());}
    @PostMapping("/push/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal V3Principal p,@RequestHeader(value=HttpHeaders.USER_AGENT,required=false)String agent,@Valid @RequestBody SubscribeRequest r){push.subscribe(p.accountId(),r.endpoint(),r.p256dh(),r.auth(),agent);}
    @DeleteMapping("/push/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal V3Principal p,@Valid @RequestBody UnsubscribeRequest r){push.unsubscribe(p.accountId(),r.endpoint());}

    public record UnreadCount(long count) {
    }
    public record PushKey(String publicKey){}
    public record SubscribeRequest(@NotBlank @Size(max=4096)String endpoint,@NotBlank @Size(max=255)String p256dh,@NotBlank @Size(max=255)String auth){}
    public record UnsubscribeRequest(@NotBlank @Size(max=4096)String endpoint){}
}

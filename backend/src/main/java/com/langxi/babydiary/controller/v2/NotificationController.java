package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.NotificationVO;
import com.langxi.babydiary.dto.PushSubscriptionDTO;
import com.langxi.babydiary.dto.PushUnsubscribeDTO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.NotificationService;
import com.langxi.babydiary.service.WebPushService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final WebPushService webPushService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService,
                                  WebPushService webPushService,
                                  CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.webPushService = webPushService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Result<PageResult<NotificationVO>> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.success(notificationService.list(currentUser.getUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(notificationService.unreadCount(currentUser.getUserId()));
    }

    @PutMapping("/{notificationId}/read")
    public Result<Void> markRead(@PathVariable String notificationId) {
        notificationService.markRead(currentUser.getUserId(), notificationId);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(currentUser.getUserId());
        return Result.success();
    }

    @GetMapping("/push/public-key")
    public Result<String> pushPublicKey() {
        return Result.success(webPushService.publicKey());
    }

    @PostMapping("/push/subscriptions")
    public Result<Void> subscribe(@Valid @RequestBody PushSubscriptionDTO dto,
                                  @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        notificationService.subscribe(currentUser.getUserId(), dto, userAgent);
        return Result.success("推送通知已开启", null);
    }

    @DeleteMapping("/push/subscriptions")
    public Result<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeDTO dto) {
        notificationService.unsubscribe(currentUser.getUserId(), dto.getEndpoint());
        return Result.success("推送通知已关闭", null);
    }
}

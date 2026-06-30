package ru.wisla.fm.notifications.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final PushNotificationService pushNotificationService;

    public NotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @GetMapping("/push")
    public PushNotificationListDto listPush(@RequestParam(required = false) String since) {
        Instant sinceInstant = since != null && !since.isBlank() ? Instant.parse(since) : Instant.EPOCH;
        return pushNotificationService.listSince(sinceInstant);
    }
}

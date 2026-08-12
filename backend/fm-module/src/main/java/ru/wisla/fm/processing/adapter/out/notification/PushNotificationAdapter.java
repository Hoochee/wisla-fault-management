package ru.wisla.fm.processing.adapter.out.notification;

import org.springframework.stereotype.Component;
import ru.wisla.fm.notifications.api.PushNotificationService;
import ru.wisla.fm.processing.application.port.out.PushNotificationPort;

import java.util.UUID;

@Component
public class PushNotificationAdapter implements PushNotificationPort {

    private final PushNotificationService pushNotificationService;

    public PushNotificationAdapter(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @Override
    public void createPush(UUID ruleId, UUID eventId, String title, String message) {
        pushNotificationService.create(ruleId, eventId, title, message);
    }
}

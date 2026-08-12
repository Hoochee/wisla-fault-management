package ru.wisla.fm.processing.application.port.out;

import java.util.UUID;

/** Records the in-app push notification a push action produces. */
public interface PushNotificationPort {

    void createPush(UUID ruleId, UUID eventId, String title, String message);
}

package ru.wisla.fm.notifications.api;

import java.time.Instant;
import java.util.UUID;

public record PushNotificationDto(
        UUID id,
        UUID ruleId,
        UUID eventId,
        String title,
        String message,
        Instant createdAt
) {
}

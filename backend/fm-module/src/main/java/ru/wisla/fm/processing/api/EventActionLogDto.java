package ru.wisla.fm.processing.api;

import java.time.Instant;
import java.util.UUID;

public record EventActionLogDto(
        UUID id,
        UUID eventId,
        String action,
        String userName,
        UUID userId,
        Instant timestamp,
        String details
) {
}

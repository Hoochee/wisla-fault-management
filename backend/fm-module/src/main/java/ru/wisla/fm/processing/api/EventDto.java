package ru.wisla.fm.processing.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDto(
        UUID id,
        String status,
        String severity,
        String title,
        String description,
        UUID assignedUserId,
        String assignedUserName,
        int repeatCount,
        UUID ciId,
        String nodeFqdn,
        String systemName,
        String subsystemName,
        UUID sourceId,
        String sourceName,
        UUID rootEventId,
        List<UUID> childEventIds,
        String itsmIncidentNumber,
        List<String> tags,
        Instant createdAt,
        Instant sourceAt,
        Instant lastRepeatAt,
        Instant takenAt,
        Instant closedAt,
        Instant updatedAt,
        Instant acknowledgedAt,
        UUID acknowledgedByUserId,
        Instant silencedUntil,
        UUID silencedByUserId
) {
}

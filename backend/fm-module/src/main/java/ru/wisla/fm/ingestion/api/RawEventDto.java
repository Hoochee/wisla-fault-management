package ru.wisla.fm.ingestion.api;

import java.time.Instant;
import java.util.UUID;

public record RawEventDto(
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
        java.util.List<UUID> childEventIds,
        String itsmIncidentNumber,
        java.util.List<String> tags,
        Instant createdAt,
        Instant sourceAt,
        Instant lastRepeatAt,
        Instant takenAt,
        Instant closedAt,
        Instant updatedAt,
        boolean isRaw,
        boolean processed,
        UUID processedEventId,
        UUID ingestBatchId
) {
}

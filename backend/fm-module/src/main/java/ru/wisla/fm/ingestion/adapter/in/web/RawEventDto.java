package ru.wisla.fm.ingestion.adapter.in.web;

import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.RawEventListing;

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

    public static RawEventDto from(RawEventListing.Item item) {
        RawEvent raw = item.rawEvent();
        return new RawEventDto(
                raw.id(),
                raw.status(),
                raw.severity(),
                raw.title(),
                raw.description(),
                null,
                null,
                1,
                raw.ciId(),
                raw.nodeFqdn(),
                null,
                null,
                raw.sourceId(),
                item.sourceName(),
                null,
                java.util.List.of(),
                null,
                java.util.List.of(),
                raw.createdAt(),
                raw.sourceAt(),
                null,
                null,
                null,
                raw.updatedAt(),
                true,
                raw.processed(),
                raw.processedEventId(),
                raw.ingestBatchId());
    }
}

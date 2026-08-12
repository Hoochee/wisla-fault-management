package ru.wisla.fm.ingestion.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A raw event as received from a source, before processing turns it into an event.
 * {@code attributes} and {@code rawPayload} stay maps here; serialization into the
 * {@code raw_events} jsonb columns belongs to the persistence adapter.
 */
public record RawEvent(
        UUID id,
        UUID sourceId,
        String externalId,
        String title,
        String description,
        String severity,
        String status,
        String nodeFqdn,
        UUID ciId,
        Map<String, Object> attributes,
        Map<String, Object> rawPayload,
        Instant sourceAt,
        UUID ingestBatchId,
        boolean processed,
        UUID processedEventId,
        String processingError,
        Instant createdAt,
        Instant updatedAt
) {

    public static final String DEFAULT_STATUS = "new";

    public static RawEvent incoming(
            UUID sourceId,
            String externalId,
            String title,
            String description,
            String severity,
            String status,
            String nodeFqdn,
            Instant sourceAt,
            Map<String, Object> attributes,
            Map<String, Object> rawPayload,
            UUID ingestBatchId
    ) {
        return new RawEvent(
                null,
                sourceId,
                externalId,
                title,
                description,
                severity,
                status != null ? status : DEFAULT_STATUS,
                nodeFqdn,
                null,
                attributes != null ? attributes : Map.of(),
                rawPayload != null ? rawPayload : Map.of(),
                sourceAt,
                ingestBatchId,
                false,
                null,
                null,
                null,
                null);
    }
}

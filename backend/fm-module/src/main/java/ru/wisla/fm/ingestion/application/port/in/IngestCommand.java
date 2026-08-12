package ru.wisla.fm.ingestion.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single entry shape for both the HTTP and the Kafka ingest path. The {@code sourceId} is already
 * authenticated (HTTP) or trusted (Kafka) by the time the command is built.
 */
public record IngestCommand(
        UUID sourceId,
        Boolean heartbeat,
        List<IngestEvent> events,
        String adapterVersion,
        Instant receivedAt
) {

    public boolean isHeartbeat() {
        return Boolean.TRUE.equals(heartbeat);
    }

    public List<IngestEvent> eventsOrEmpty() {
        return events != null ? events : List.of();
    }

    public record IngestEvent(
            String externalId,
            String title,
            String description,
            String severity,
            String status,
            Instant occurredAt,
            String nodeFqdn,
            Map<String, Object> attributes,
            Map<String, Object> rawPayload
    ) {
    }
}

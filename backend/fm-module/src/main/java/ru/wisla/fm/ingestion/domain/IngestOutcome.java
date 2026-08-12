package ru.wisla.fm.ingestion.domain;

import java.util.List;
import java.util.UUID;

/**
 * Result of one ingest call. {@code heartbeatAck} is {@code null} for event batches.
 */
public record IngestOutcome(
        int accepted,
        int rejected,
        List<UUID> rawEventIds,
        Boolean heartbeatAck
) {

    public static IngestOutcome heartbeatAcknowledged() {
        return new IngestOutcome(0, 0, List.of(), true);
    }
}

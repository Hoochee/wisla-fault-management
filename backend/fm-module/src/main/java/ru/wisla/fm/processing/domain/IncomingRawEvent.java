package ru.wisla.fm.processing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw event as the processing context reads it. This is a processing-owned read model on purpose:
 * {@code processing/domain} must not import {@code ingestion.domain}, and the {@code raw_events}
 * jsonb {@code payload} stays a {@code String} so nothing is re-serialized (design decision D2).
 */
public record IncomingRawEvent(
        UUID id,
        UUID sourceId,
        String externalId,
        String title,
        String description,
        String severity,
        String status,
        String nodeFqdn,
        UUID ciId,
        String payload,
        Instant sourceAt,
        boolean processed
) {
}

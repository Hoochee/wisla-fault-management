package com.wisla.fm.adapter.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka value for topic {@code fm.raw-events}. {@code body} matches adapter {@code IngestRequest} fields.
 * Source API secret must never be included.
 */
public record RawEventEnvelope(
        int schemaVersion,
        UUID messageId,
        Instant producedAt,
        UUID sourceId,
        String sourceKey,
        Map<String, Object> body
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}

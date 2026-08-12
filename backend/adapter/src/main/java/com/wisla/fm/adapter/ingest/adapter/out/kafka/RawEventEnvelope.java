package com.wisla.fm.adapter.ingest.adapter.out.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka value for topic {@code fm.raw-events}. {@code body} matches adapter {@code IngestRequest} fields.
 * Source API secret must never be included.
 *
 * <p>This is the adapter's own private copy of the wire contract: fm-module owns a structurally
 * different record with the same JSON field names. The duplication is deliberate — see decision D0
 * in the hexagonal-refactor design.</p>
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

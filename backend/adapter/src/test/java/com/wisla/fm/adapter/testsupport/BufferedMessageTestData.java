package com.wisla.fm.adapter.testsupport;

import com.wisla.fm.adapter.ingest.adapter.out.persistence.BufferedMessageJpaEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a {@code buffered_messages} row the way the removed {@code BufferedMessage} constructor
 * did: a fresh id, {@code retryCount = 0} and {@code createdAt = updatedAt = now}.
 */
public final class BufferedMessageTestData {

    private BufferedMessageTestData() {
    }

    public static BufferedMessageJpaEntity entity(
            UUID sourceId,
            String ingestApiKey,
            Map<String, Object> payload,
            Instant nextRetryAt
    ) {
        Instant now = Instant.now();
        return new BufferedMessageJpaEntity(
                UUID.randomUUID(),
                sourceId,
                ingestApiKey,
                payload,
                0,
                nextRetryAt,
                now,
                now
        );
    }
}

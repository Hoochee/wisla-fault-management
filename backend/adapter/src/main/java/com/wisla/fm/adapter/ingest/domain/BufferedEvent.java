package com.wisla.fm.adapter.ingest.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook payload parked for a later delivery attempt, owning the exponential retry backoff
 * {@code base * 2^min(retryCount - 1, 10)}.
 */
public final class BufferedEvent {

    private static final int MAX_BACKOFF_EXPONENT = 10;

    private final UUID id;
    private final UUID sourceId;
    private final String ingestApiKey;
    private final Map<String, Object> payload;
    private final Instant createdAt;
    private int retryCount;
    private Instant nextRetryAt;
    private Instant updatedAt;

    public BufferedEvent(
            UUID id,
            UUID sourceId,
            String ingestApiKey,
            Map<String, Object> payload,
            int retryCount,
            Instant nextRetryAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.ingestApiKey = ingestApiKey;
        this.payload = payload;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BufferedEvent create(
            UUID sourceId,
            String ingestApiKey,
            Map<String, Object> payload,
            Instant nextRetryAt,
            Instant now
    ) {
        return new BufferedEvent(UUID.randomUUID(), sourceId, ingestApiKey, payload, 0, nextRetryAt, now, now);
    }

    public void scheduleRetry(int baseSeconds, Instant now) {
        this.retryCount++;
        int exponent = Math.min(this.retryCount - 1, MAX_BACKOFF_EXPONENT);
        long delaySeconds = (long) baseSeconds * (1L << exponent);
        this.nextRetryAt = now.plusSeconds(delaySeconds);
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public String ingestApiKey() {
        return ingestApiKey;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public int retryCount() {
        return retryCount;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

package com.wisla.fm.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "buffered_messages")
public class BufferedMessage {

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "ingest_api_key", length = 512)
    private String ingestApiKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BufferedMessage() {
    }

    public BufferedMessage(UUID sourceId, String ingestApiKey, Map<String, Object> payload, Instant nextRetryAt) {
        this.id = UUID.randomUUID();
        this.sourceId = sourceId;
        this.ingestApiKey = ingestApiKey;
        this.payload = payload;
        this.retryCount = 0;
        this.nextRetryAt = nextRetryAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getIngestApiKey() {
        return ingestApiKey;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void scheduleRetry(int baseSeconds) {
        this.retryCount++;
        int exponent = Math.min(this.retryCount - 1, 10);
        long delaySeconds = (long) baseSeconds * (1L << exponent);
        this.nextRetryAt = Instant.now().plusSeconds(delaySeconds);
        this.updatedAt = Instant.now();
    }
}

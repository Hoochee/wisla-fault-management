package ru.wisla.fm.ingestion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_events")
public class RawEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 16)
    private String status = "new";

    @Column(name = "node_fqdn", length = 512)
    private String nodeFqdn;

    @Column(name = "ci_id")
    private UUID ciId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private String rawPayload = "{}";

    @Column(name = "source_at", nullable = false)
    private Instant sourceAt;

    @Column(name = "ingest_batch_id")
    private UUID ingestBatchId;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "processed_event_id")
    private UUID processedEventId;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNodeFqdn() {
        return nodeFqdn;
    }

    public void setNodeFqdn(String nodeFqdn) {
        this.nodeFqdn = nodeFqdn;
    }

    public UUID getCiId() {
        return ciId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public Instant getSourceAt() {
        return sourceAt;
    }

    public void setSourceAt(Instant sourceAt) {
        this.sourceAt = sourceAt;
    }

    public UUID getIngestBatchId() {
        return ingestBatchId;
    }

    public void setIngestBatchId(UUID ingestBatchId) {
        this.ingestBatchId = ingestBatchId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public UUID getProcessedEventId() {
        return processedEventId;
    }

    public void setProcessedEventId(UUID processedEventId) {
        this.processedEventId = processedEventId;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public void setCiId(UUID ciId) {
        this.ciId = ciId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

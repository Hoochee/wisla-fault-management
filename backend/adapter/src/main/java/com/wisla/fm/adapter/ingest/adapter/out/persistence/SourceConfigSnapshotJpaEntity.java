package com.wisla.fm.adapter.ingest.adapter.out.persistence;

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
@Table(name = "source_config_snapshots")
public class SourceConfigSnapshotJpaEntity {

    @Id
    @Column(name = "source_id")
    private UUID sourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_rules", nullable = false)
    private Map<String, Object> filterRules;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "ttl_expires_at", nullable = false)
    private Instant ttlExpiresAt;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "source_type", nullable = false)
    private String sourceType = "push_rest";

    @Column(name = "schedule")
    private String schedule;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parser_config", nullable = false)
    private Map<String, Object> parserConfig = Map.of();

    protected SourceConfigSnapshotJpaEntity() {
    }

    public static SourceConfigSnapshotJpaEntity createEmpty() {
        return new SourceConfigSnapshotJpaEntity();
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public Map<String, Object> getFilterRules() {
        return filterRules;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Instant getTtlExpiresAt() {
        return ttlExpiresAt;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSchedule() {
        return schedule;
    }

    public Map<String, Object> getParserConfig() {
        return parserConfig;
    }

    public boolean isExpired() {
        return ttlExpiresAt.isBefore(Instant.now());
    }

    public void replace(
            UUID sourceId,
            String sourceKey,
            String apiKeyHash,
            String endpoint,
            Map<String, Object> filterRules,
            boolean blocked,
            Instant ttlExpiresAt,
            Instant now
    ) {
        replace(sourceId, sourceKey, apiKeyHash, endpoint, filterRules, blocked, ttlExpiresAt, now,
                "push_rest", null, Map.of());
    }

    public void replace(
            UUID sourceId,
            String sourceKey,
            String apiKeyHash,
            String endpoint,
            Map<String, Object> filterRules,
            boolean blocked,
            Instant ttlExpiresAt,
            Instant now,
            String sourceType,
            String schedule,
            Map<String, Object> parserConfig
    ) {
        this.sourceId = sourceId;
        this.sourceKey = sourceKey;
        this.apiKeyHash = apiKeyHash;
        this.endpoint = endpoint;
        this.filterRules = filterRules != null ? filterRules : Map.of();
        this.blocked = blocked;
        this.ttlExpiresAt = ttlExpiresAt;
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        this.sourceType = sourceType != null && !sourceType.isBlank() ? sourceType : "push_rest";
        this.schedule = schedule;
        this.parserConfig = parserConfig != null ? parserConfig : Map.of();
    }
}

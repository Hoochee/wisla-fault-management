package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pull_metric_states")
@IdClass(PullMetricStateJpaEntity.Pk.class)
public class PullMetricStateJpaEntity {

    @Id
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Id
    @Column(name = "external_id", nullable = false, length = 512)
    private String externalId;

    @Column(name = "last_severity", length = 16)
    private String lastSeverity;

    @Column(name = "last_value")
    private Double lastValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PullMetricStateJpaEntity() {
    }

    public PullMetricStateJpaEntity(
            UUID sourceId,
            String externalId,
            String lastSeverity,
            Double lastValue,
            Instant updatedAt
    ) {
        this.sourceId = sourceId;
        this.externalId = externalId;
        this.lastSeverity = lastSeverity;
        this.lastValue = lastValue;
        this.updatedAt = updatedAt;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getLastSeverity() {
        return lastSeverity;
    }

    public Double getLastValue() {
        return lastValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void apply(String lastSeverity, Double lastValue, Instant updatedAt) {
        this.lastSeverity = lastSeverity;
        this.lastValue = lastValue;
        this.updatedAt = updatedAt;
    }

    public static final class Pk implements Serializable {

        private UUID sourceId;
        private String externalId;

        public Pk() {
        }

        public Pk(UUID sourceId, String externalId) {
            this.sourceId = sourceId;
            this.externalId = externalId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(sourceId, pk.sourceId) && Objects.equals(externalId, pk.externalId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceId, externalId);
        }
    }
}

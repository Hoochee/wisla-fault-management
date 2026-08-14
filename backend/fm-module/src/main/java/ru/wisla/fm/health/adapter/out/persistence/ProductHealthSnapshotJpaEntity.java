package ru.wisla.fm.health.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_health_snapshot")
public class ProductHealthSnapshotJpaEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "health_percent", nullable = false)
    private int healthPercent;

    @Column(name = "damage_percent", nullable = false)
    private int damagePercent;

    @Column(name = "max_severity", nullable = false, length = 16)
    private String maxSeverity;

    @Column(name = "active_event_count", nullable = false)
    private int activeEventCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public int getHealthPercent() {
        return healthPercent;
    }

    public void setHealthPercent(int healthPercent) {
        this.healthPercent = healthPercent;
    }

    public int getDamagePercent() {
        return damagePercent;
    }

    public void setDamagePercent(int damagePercent) {
        this.damagePercent = damagePercent;
    }

    public String getMaxSeverity() {
        return maxSeverity;
    }

    public void setMaxSeverity(String maxSeverity) {
        this.maxSeverity = maxSeverity;
    }

    public int getActiveEventCount() {
        return activeEventCount;
    }

    public void setActiveEventCount(int activeEventCount) {
        this.activeEventCount = activeEventCount;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}

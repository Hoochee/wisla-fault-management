package ru.wisla.fm.health.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "product_health_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "bucket_start"})
)
public class ProductHealthHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "bucket_minutes", nullable = false)
    private int bucketMinutes;

    @Column(name = "min_health", nullable = false)
    private int minHealth;

    @Column(name = "max_health", nullable = false)
    private int maxHealth;

    @Column(name = "worst_severity", nullable = false, length = 16)
    private String worstSeverity;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Instant getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(Instant bucketStart) {
        this.bucketStart = bucketStart;
    }

    public int getBucketMinutes() {
        return bucketMinutes;
    }

    public void setBucketMinutes(int bucketMinutes) {
        this.bucketMinutes = bucketMinutes;
    }

    public int getMinHealth() {
        return minHealth;
    }

    public void setMinHealth(int minHealth) {
        this.minHealth = minHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public String getWorstSeverity() {
        return worstSeverity;
    }

    public void setWorstSeverity(String worstSeverity) {
        this.worstSeverity = worstSeverity;
    }
}

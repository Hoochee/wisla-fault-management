package ru.wisla.fm.cmdb.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "product_ci")
public class ProductCiEntity {

    @EmbeddedId
    private ProductCiId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public ProductCiId getId() {
        return id;
    }

    public void setId(ProductCiId id) {
        this.id = id;
    }
}

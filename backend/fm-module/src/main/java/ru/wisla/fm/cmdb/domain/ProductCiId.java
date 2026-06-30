package ru.wisla.fm.cmdb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductCiId implements Serializable {

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "ci_id")
    private UUID ciId;

    public ProductCiId() {
    }

    public ProductCiId(UUID productId, UUID ciId) {
        this.productId = productId;
        this.ciId = ciId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getCiId() {
        return ciId;
    }

    public void setCiId(UUID ciId) {
        this.ciId = ciId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductCiId that)) {
            return false;
        }
        return Objects.equals(productId, that.productId) && Objects.equals(ciId, that.ciId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, ciId);
    }
}

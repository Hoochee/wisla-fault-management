package ru.wisla.fm.health.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductComponentCiId implements Serializable {

    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "ci_id")
    private UUID ciId;

    public ProductComponentCiId() {
    }

    public ProductComponentCiId(UUID componentId, UUID ciId) {
        this.componentId = componentId;
        this.ciId = ciId;
    }

    public UUID getComponentId() {
        return componentId;
    }

    public UUID getCiId() {
        return ciId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProductComponentCiId that)) {
            return false;
        }
        return Objects.equals(componentId, that.componentId) && Objects.equals(ciId, that.ciId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, ciId);
    }
}

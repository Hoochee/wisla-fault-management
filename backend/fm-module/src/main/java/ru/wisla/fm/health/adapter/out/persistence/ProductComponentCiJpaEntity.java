package ru.wisla.fm.health.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_component_ci")
public class ProductComponentCiJpaEntity {

    @EmbeddedId
    private ProductComponentCiId id;

    @Column
    private Integer weight;

    public ProductComponentCiId getId() {
        return id;
    }

    public void setId(ProductComponentCiId id) {
        this.id = id;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }
}

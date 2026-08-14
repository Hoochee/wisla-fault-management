package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductComponentCiJpaRepository extends JpaRepository<ProductComponentCiJpaEntity, ProductComponentCiId> {

    List<ProductComponentCiJpaEntity> findByIdComponentIdIn(Collection<UUID> componentIds);

    List<ProductComponentCiJpaEntity> findByIdCiId(UUID ciId);

    void deleteByIdComponentIdIn(Collection<UUID> componentIds);
}

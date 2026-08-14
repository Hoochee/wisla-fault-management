package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductComponentJpaRepository extends JpaRepository<ProductComponentJpaEntity, UUID> {

    List<ProductComponentJpaEntity> findByProductIdOrderBySortOrderAsc(UUID productId);

    Optional<ProductComponentJpaEntity> findByProductIdAndCode(UUID productId, String code);

    void deleteByProductId(UUID productId);
}

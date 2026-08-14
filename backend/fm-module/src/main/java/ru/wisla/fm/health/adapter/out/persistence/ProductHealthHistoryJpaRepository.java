package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductHealthHistoryJpaRepository extends JpaRepository<ProductHealthHistoryJpaEntity, UUID> {

    Optional<ProductHealthHistoryJpaEntity> findByProductIdAndBucketStart(UUID productId, Instant bucketStart);

    List<ProductHealthHistoryJpaEntity> findByProductIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(
            UUID productId,
            Instant from,
            Instant to
    );
}

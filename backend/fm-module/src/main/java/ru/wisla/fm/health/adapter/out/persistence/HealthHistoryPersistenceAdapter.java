package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.health.application.port.out.HealthHistoryStorePort;
import ru.wisla.fm.health.domain.HealthHistoryBucket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class HealthHistoryPersistenceAdapter implements HealthHistoryStorePort {

    private final ProductHealthHistoryJpaRepository repository;

    public HealthHistoryPersistenceAdapter(ProductHealthHistoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void upsertBucket(HealthHistoryBucket bucket) {
        ProductHealthHistoryJpaEntity entity = repository
                .findByProductIdAndBucketStart(bucket.productId(), bucket.bucketStart())
                .orElseGet(ProductHealthHistoryJpaEntity::new);
        entity.setProductId(bucket.productId());
        entity.setBucketStart(bucket.bucketStart());
        entity.setBucketMinutes(bucket.bucketMinutes());
        entity.setMinHealth(bucket.minHealth());
        entity.setMaxHealth(bucket.maxHealth());
        entity.setWorstSeverity(bucket.worstSeverity());
        repository.save(entity);
    }

    @Override
    public List<HealthHistoryBucket> findRange(UUID productId, Instant from, Instant to) {
        return repository.findByProductIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(productId, from, to)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private HealthHistoryBucket toDomain(ProductHealthHistoryJpaEntity entity) {
        return new HealthHistoryBucket(
                entity.getId(),
                entity.getProductId(),
                entity.getBucketStart(),
                entity.getBucketMinutes(),
                entity.getMinHealth(),
                entity.getMaxHealth(),
                entity.getWorstSeverity()
        );
    }
}

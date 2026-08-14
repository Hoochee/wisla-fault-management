package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.health.application.port.out.HealthSnapshotStorePort;
import ru.wisla.fm.health.domain.ProductHealthSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class HealthSnapshotPersistenceAdapter implements HealthSnapshotStorePort {

    private final ProductHealthSnapshotJpaRepository repository;
    private final SnapshotPayloadMapper payloadMapper;

    public HealthSnapshotPersistenceAdapter(
            ProductHealthSnapshotJpaRepository repository,
            SnapshotPayloadMapper payloadMapper
    ) {
        this.repository = repository;
        this.payloadMapper = payloadMapper;
    }

    @Override
    @Transactional
    public void upsert(ProductHealthSnapshot snapshot) {
        ProductHealthSnapshotJpaEntity entity = repository.findById(snapshot.productId())
                .orElseGet(ProductHealthSnapshotJpaEntity::new);
        entity.setProductId(snapshot.productId());
        entity.setHealthPercent(snapshot.healthPercent());
        entity.setDamagePercent(snapshot.damagePercent());
        entity.setMaxSeverity(snapshot.maxSeverity());
        entity.setActiveEventCount(snapshot.activeEventCount());
        entity.setPayload(payloadMapper.toJson(snapshot.payload()));
        entity.setCalculatedAt(snapshot.calculatedAt());
        repository.save(entity);
    }

    @Override
    public Optional<ProductHealthSnapshot> findByProductId(UUID productId) {
        return repository.findById(productId).map(this::toDomain);
    }

    @Override
    public List<ProductHealthSnapshot> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private ProductHealthSnapshot toDomain(ProductHealthSnapshotJpaEntity entity) {
        return new ProductHealthSnapshot(
                entity.getProductId(),
                entity.getHealthPercent(),
                entity.getDamagePercent(),
                entity.getMaxSeverity(),
                entity.getActiveEventCount(),
                payloadMapper.fromJson(entity.getPayload()),
                entity.getCalculatedAt()
        );
    }
}

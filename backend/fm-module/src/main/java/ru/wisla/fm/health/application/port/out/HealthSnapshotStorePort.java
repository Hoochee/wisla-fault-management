package ru.wisla.fm.health.application.port.out;

import ru.wisla.fm.health.domain.ProductHealthSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HealthSnapshotStorePort {

    void upsert(ProductHealthSnapshot snapshot);

    Optional<ProductHealthSnapshot> findByProductId(UUID productId);

    List<ProductHealthSnapshot> findAll();
}

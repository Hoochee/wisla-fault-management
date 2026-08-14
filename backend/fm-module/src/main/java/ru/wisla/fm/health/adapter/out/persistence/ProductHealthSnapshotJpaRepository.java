package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductHealthSnapshotJpaRepository extends JpaRepository<ProductHealthSnapshotJpaEntity, UUID> {
}

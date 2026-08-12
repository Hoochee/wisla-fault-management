package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceConfigSnapshotJpaRepository extends JpaRepository<SourceConfigSnapshotJpaEntity, UUID> {

    Optional<SourceConfigSnapshotJpaEntity> findBySourceKey(String sourceKey);
}

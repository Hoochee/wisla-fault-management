package com.wisla.fm.adapter.persistence.repository;

import com.wisla.fm.adapter.persistence.entity.SourceConfigSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceConfigSnapshotRepository extends JpaRepository<SourceConfigSnapshot, UUID> {

    Optional<SourceConfigSnapshot> findBySourceKey(String sourceKey);
}

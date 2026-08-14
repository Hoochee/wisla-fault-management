package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PullMetricStateJpaRepository
        extends JpaRepository<PullMetricStateJpaEntity, PullMetricStateJpaEntity.Pk> {
}

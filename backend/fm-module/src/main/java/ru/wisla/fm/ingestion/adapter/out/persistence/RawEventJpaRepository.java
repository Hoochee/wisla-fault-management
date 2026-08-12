package ru.wisla.fm.ingestion.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface RawEventJpaRepository
        extends JpaRepository<RawEventJpaEntity, UUID>, JpaSpecificationExecutor<RawEventJpaEntity> {

    Page<RawEventJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

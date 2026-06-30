package ru.wisla.fm.ingestion.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.wisla.fm.ingestion.domain.RawEventEntity;

import java.util.UUID;

public interface RawEventRepository extends JpaRepository<RawEventEntity, UUID>, JpaSpecificationExecutor<RawEventEntity> {

    Page<RawEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

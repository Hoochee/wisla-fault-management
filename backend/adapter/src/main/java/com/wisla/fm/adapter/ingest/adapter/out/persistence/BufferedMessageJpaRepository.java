package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BufferedMessageJpaRepository extends JpaRepository<BufferedMessageJpaEntity, UUID> {

    long count();

    @Query("SELECT b FROM BufferedMessageJpaEntity b WHERE b.nextRetryAt <= :now ORDER BY b.nextRetryAt ASC")
    List<BufferedMessageJpaEntity> findReadyForRetry(Instant now);
}

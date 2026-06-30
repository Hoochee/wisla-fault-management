package com.wisla.fm.adapter.persistence.repository;

import com.wisla.fm.adapter.persistence.entity.BufferedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BufferedMessageRepository extends JpaRepository<BufferedMessage, UUID> {

    long count();

    @Query("SELECT b FROM BufferedMessage b WHERE b.nextRetryAt <= :now ORDER BY b.nextRetryAt ASC")
    List<BufferedMessage> findReadyForRetry(Instant now);
}

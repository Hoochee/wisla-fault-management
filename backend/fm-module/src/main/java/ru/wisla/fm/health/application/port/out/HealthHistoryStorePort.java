package ru.wisla.fm.health.application.port.out;

import ru.wisla.fm.health.domain.HealthHistoryBucket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HealthHistoryStorePort {

    void upsertBucket(HealthHistoryBucket bucket);

    List<HealthHistoryBucket> findRange(UUID productId, Instant from, Instant to);
}

package ru.wisla.fm.health.application.port.in;

import ru.wisla.fm.health.domain.HealthHistoryBucket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GetProductHealthHistoryUseCase {

    List<HealthHistoryBucket> history(UUID productId, Instant from, Instant to, int bucketMinutes);
}

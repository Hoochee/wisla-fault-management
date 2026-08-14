package ru.wisla.fm.health.domain;

import java.time.Instant;
import java.util.UUID;

public record HealthHistoryBucket(
        UUID id,
        UUID productId,
        Instant bucketStart,
        int bucketMinutes,
        int minHealth,
        int maxHealth,
        String worstSeverity
) {
    public static Instant floorToBucket(Instant instant, int bucketMinutes) {
        long seconds = instant.getEpochSecond();
        long bucketSeconds = (long) bucketMinutes * 60L;
        long floored = Math.floorDiv(seconds, bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(floored);
    }
}

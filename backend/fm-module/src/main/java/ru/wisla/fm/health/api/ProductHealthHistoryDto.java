package ru.wisla.fm.health.api;

import java.time.Instant;

public record ProductHealthHistoryDto(
        Instant bucketStart,
        int bucketMinutes,
        int minHealth,
        int maxHealth,
        String worstSeverity
) {
}

package ru.wisla.fm.health.domain;

import java.time.Instant;

public record ProductHealthDetail(
        ProductHealthView summary,
        SnapshotPayload payload,
        Instant calculatedAt,
        Integer minHealthToday,
        Integer maxHealthToday
) {
}

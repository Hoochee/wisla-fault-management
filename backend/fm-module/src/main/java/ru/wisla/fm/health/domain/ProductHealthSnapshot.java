package ru.wisla.fm.health.domain;

import java.time.Instant;
import java.util.UUID;

public record ProductHealthSnapshot(
        UUID productId,
        int healthPercent,
        int damagePercent,
        String maxSeverity,
        int activeEventCount,
        SnapshotPayload payload,
        Instant calculatedAt
) {
}

package ru.wisla.fm.health.domain;

import java.util.UUID;

public record ComponentHealth(
        UUID id,
        String code,
        String name,
        int weight,
        String influenceType,
        int criticalThreshold,
        int healthPercent,
        int damagePercent,
        java.util.List<UUID> ciIds
) {
}

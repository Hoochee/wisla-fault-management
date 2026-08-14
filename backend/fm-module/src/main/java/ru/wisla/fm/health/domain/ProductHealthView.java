package ru.wisla.fm.health.domain;

import java.util.List;
import java.util.UUID;

public record ProductHealthView(
        UUID id,
        String name,
        String tenant,
        String site,
        String maxSeverity,
        int activeEventCount,
        List<UUID> ciIds,
        List<String> tags,
        int healthPercent,
        int damagePercent,
        List<ComponentHealth> components
) {
}

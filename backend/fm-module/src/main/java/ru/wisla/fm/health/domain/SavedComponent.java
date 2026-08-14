package ru.wisla.fm.health.domain;

import java.util.List;
import java.util.UUID;

public record SavedComponent(
        UUID id,
        String code,
        String name,
        int weight,
        String influenceType,
        int criticalThreshold,
        int sortOrder,
        List<UUID> ciIds
) {
}

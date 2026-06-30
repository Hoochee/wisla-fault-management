package ru.wisla.fm.health.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductHealthDto(
        UUID id,
        String name,
        String tenant,
        String site,
        String maxSeverity,
        int activeEventCount,
        List<UUID> ciIds,
        List<String> tags
) {
}

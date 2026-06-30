package ru.wisla.fm.dashboard.api;

import java.util.List;
import java.util.UUID;

public record ProductHealthPreview(
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

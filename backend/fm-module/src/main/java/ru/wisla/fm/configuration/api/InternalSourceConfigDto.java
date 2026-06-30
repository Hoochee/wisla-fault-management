package ru.wisla.fm.configuration.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InternalSourceConfigDto(
        UUID sourceId,
        String apiKeyHash,
        String status,
        Map<String, Object> filterRules,
        String endpoint,
        String type,
        Instant updatedAt
) {
}

package ru.wisla.fm.configuration.api;

import java.util.Map;
import java.util.UUID;

public record InternalSourceIndexDto(
        UUID sourceId,
        String sourceKey,
        String apiKeyHash,
        String status,
        Map<String, Object> filterRules,
        String type,
        String schedule,
        Map<String, Object> parserConfig
) {
}

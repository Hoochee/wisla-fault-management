package ru.wisla.fm.configuration.api;

import java.util.Map;

public record EventSourcePatch(
        String name,
        String protocol,
        String endpoint,
        String schedule,
        String status,
        Map<String, Object> filterRules,
        Map<String, Object> parserConfig,
        Boolean regenerateApiKey
) {
}

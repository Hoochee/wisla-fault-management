package ru.wisla.fm.configuration.api;

import java.util.Map;

public record EventSourceDetailDto(
        EventSourceDto source,
        Map<String, Object> filterRules,
        Map<String, Object> parserConfig,
        String webhookUrl
) {
}

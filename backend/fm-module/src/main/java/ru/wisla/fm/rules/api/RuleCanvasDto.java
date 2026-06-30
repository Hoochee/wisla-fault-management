package ru.wisla.fm.rules.api;

import java.util.List;
import java.util.Map;

public record RuleCanvasDto(
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges
) {
}

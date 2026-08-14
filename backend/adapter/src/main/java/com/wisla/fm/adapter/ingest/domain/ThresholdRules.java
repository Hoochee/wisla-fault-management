package com.wisla.fm.adapter.ingest.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ThresholdRules {

    private ThresholdRules() {
    }

    public static List<ThresholdRule> fromParserConfig(Map<String, Object> parserConfig) {
        if (parserConfig == null) {
            return List.of();
        }
        Object raw = parserConfig.get("rules");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ThresholdRule> rules = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rules.add(fromMap(map));
            }
        }
        return List.copyOf(rules);
    }

    @SuppressWarnings("unchecked")
    private static ThresholdRule fromMap(Map<?, ?> raw) {
        Map<String, Object> typed = (Map<String, Object>) raw;
        String metric = typed.get("metric") != null ? String.valueOf(typed.get("metric")) : "";
        Map<String, Object> thresholds = typed.get("thresholds") instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : Map.of();
        boolean invert = Boolean.TRUE.equals(typed.get("invert"));
        return new ThresholdRule(
                metric,
                asDouble(thresholds.get("warning")),
                asDouble(thresholds.get("major")),
                asDouble(thresholds.get("critical")),
                invert
        );
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

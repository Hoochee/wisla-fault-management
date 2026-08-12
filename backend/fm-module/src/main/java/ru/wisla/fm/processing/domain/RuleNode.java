package ru.wisla.fm.processing.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** One node of a rule graph, flattened out of the canvas JSON. */
public record RuleNode(String id, String type, Map<String, String> config) {

    private static final Set<String> ACTION_TYPES =
            Set.of("dedup", "threshold", "correlation", "notify", "push");

    public static RuleNode fromMap(Map<String, Object> raw) {
        String id = stringVal(raw.get("id"));
        String type = stringVal(raw.get("type"));
        Map<String, String> config = extractConfig(raw);
        return new RuleNode(id, type, config);
    }

    private static Map<String, String> extractConfig(Map<String, Object> raw) {
        Map<String, String> config = new HashMap<>();
        Object data = raw.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            dataMap.forEach((k, v) -> config.put(String.valueOf(k), stringVal(v)));
        }
        Object legacyConfig = raw.get("config");
        if (legacyConfig instanceof Map<?, ?> configMap) {
            configMap.forEach((k, v) -> config.put(String.valueOf(k), stringVal(v)));
        }
        String label = stringVal(raw.get("label"));
        if (label != null && !label.isBlank()) {
            config.putIfAbsent("label", label);
        }
        return Collections.unmodifiableMap(config);
    }

    public boolean isAction() {
        return type != null && ACTION_TYPES.contains(type);
    }

    public boolean isTriggerStream() {
        return "trigger".equals(type) && "stream".equals(config.get("triggerType"));
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}

package ru.wisla.fm.processing.canvas;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record CanvasNodeView(String id, String type, Map<String, String> config) {

    private static final java.util.Set<String> ACTION_TYPES =
            java.util.Set.of("dedup", "threshold", "correlation", "notify", "push");

    public static CanvasNodeView fromMap(Map<String, Object> raw) {
        String id = stringVal(raw.get("id"));
        String type = stringVal(raw.get("type"));
        Map<String, String> config = extractConfig(raw);
        return new CanvasNodeView(id, type, config);
    }

    @SuppressWarnings("unchecked")
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

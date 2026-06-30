package ru.wisla.fm.processing.canvas;

import java.util.Map;

public record CanvasEdgeView(String id, String source, String target, String label) {

    public static CanvasEdgeView fromMap(Map<String, Object> raw) {
        String id = stringVal(raw.get("id"));
        String source = firstNonNull(raw, "source", "from");
        String target = firstNonNull(raw, "target", "to");
        String label = stringVal(raw.get("label"));
        if (id == null || id.isBlank()) {
            id = source + "->" + target;
        }
        return new CanvasEdgeView(id, source, target, label);
    }

    public boolean isDefaultBranch() {
        return label == null || label.isBlank() || "default".equalsIgnoreCase(label);
    }

    private static String firstNonNull(Map<String, Object> raw, String primary, String fallback) {
        String value = stringVal(raw.get(primary));
        if (value != null && !value.isBlank()) {
            return value;
        }
        return stringVal(raw.get(fallback));
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}

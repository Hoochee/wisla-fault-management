package ru.wisla.fm.processing.domain;

import java.util.Map;

/** One directed edge of a rule graph. Accepts both the {@code source}/{@code target} and the
 * {@code from}/{@code to} spelling the frontend sends. */
public record RuleEdge(String id, String source, String target, String label) {

    public static RuleEdge fromMap(Map<String, Object> raw) {
        String id = stringVal(raw.get("id"));
        String source = firstNonNull(raw, "source", "from");
        String target = firstNonNull(raw, "target", "to");
        String label = stringVal(raw.get("label"));
        if (id == null || id.isBlank()) {
            id = source + "->" + target;
        }
        return new RuleEdge(id, source, target, label);
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

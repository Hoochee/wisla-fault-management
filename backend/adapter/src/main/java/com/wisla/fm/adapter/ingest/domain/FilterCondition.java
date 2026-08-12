package com.wisla.fm.adapter.ingest.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One pre-filter condition: a dotted field path, an operator and an expected value.
 */
public record FilterCondition(String field, String op, Object value) {

    public static FilterCondition of(Map<String, Object> raw) {
        return new FilterCondition(
                (String) raw.get("field"),
                (String) raw.get("op"),
                raw.get("value")
        );
    }

    public boolean matches(Map<String, Object> payload) {
        if (field == null || op == null) {
            return false;
        }

        Object actual = resolveField(payload, field);

        return switch (op) {
            case "eq" -> Objects.equals(String.valueOf(actual), String.valueOf(value));
            case "ne" -> !Objects.equals(String.valueOf(actual), String.valueOf(value));
            case "contains" -> actual != null && String.valueOf(actual).contains(String.valueOf(value));
            case "in" -> value instanceof List<?> list && list.stream()
                    .anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(actual)));
            case "gt" -> compareNumbers(actual, value) > 0;
            case "lt" -> compareNumbers(actual, value) < 0;
            case "exists" -> actual != null;
            default -> false;
        };
    }

    private static Object resolveField(Map<String, Object> payload, String field) {
        if (!field.contains(".")) {
            return payload.get(field);
        }
        String[] parts = field.split("\\.");
        Object current = payload;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private static int compareNumbers(Object actual, Object expected) {
        try {
            double a = Double.parseDouble(String.valueOf(actual));
            double b = Double.parseDouble(String.valueOf(expected));
            return Double.compare(a, b);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

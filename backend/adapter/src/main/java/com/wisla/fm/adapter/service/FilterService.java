package com.wisla.fm.adapter.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FilterService {

    @SuppressWarnings("unchecked")
    public boolean shouldDrop(Map<String, Object> filterRules, Map<String, Object> payload) {
        if (filterRules == null || filterRules.isEmpty()) {
            return false;
        }

        Object enabled = filterRules.get("enabled");
        if (enabled instanceof Boolean b && !b) {
            return false;
        }

        List<Map<String, Object>> dropIf = asConditionList(filterRules.get("drop_if"));
        if (dropIf != null) {
            for (Map<String, Object> condition : dropIf) {
                if (matches(condition, payload)) {
                    return true;
                }
            }
        }

        List<Map<String, Object>> passOnly = asConditionList(filterRules.get("pass_only"));
        if (passOnly != null && !passOnly.isEmpty()) {
            boolean anyMatch = false;
            for (Map<String, Object> condition : passOnly) {
                if (matches(condition, payload)) {
                    anyMatch = true;
                    break;
                }
            }
            return !anyMatch;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asConditionList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean matches(Map<String, Object> condition, Map<String, Object> payload) {
        String field = (String) condition.get("field");
        String op = (String) condition.get("op");
        Object expected = condition.get("value");

        if (field == null || op == null) {
            return false;
        }

        Object actual = resolveField(payload, field);

        return switch (op) {
            case "eq" -> Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "ne" -> !Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "contains" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "in" -> expected instanceof List<?> list && list.stream()
                    .anyMatch(item -> Objects.equals(String.valueOf(item), String.valueOf(actual)));
            case "gt" -> compareNumbers(actual, expected) > 0;
            case "lt" -> compareNumbers(actual, expected) < 0;
            case "exists" -> actual != null;
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private Object resolveField(Map<String, Object> payload, String field) {
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

    private int compareNumbers(Object actual, Object expected) {
        try {
            double a = Double.parseDouble(String.valueOf(actual));
            double b = Double.parseDouble(String.valueOf(expected));
            return Double.compare(a, b);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

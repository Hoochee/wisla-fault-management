package com.wisla.fm.adapter.ingest.domain;

import java.util.List;
import java.util.Map;

/**
 * Pre-filter rule set of a source, kept as the raw jsonb map so the stored column round-trips
 * unchanged, with the drop decision as domain behavior.
 */
public final class FilterRules {

    private static final FilterRules EMPTY = new FilterRules(Map.of());

    private final Map<String, Object> raw;

    private FilterRules(Map<String, Object> raw) {
        this.raw = raw;
    }

    public static FilterRules of(Map<String, Object> raw) {
        return raw == null || raw.isEmpty() ? EMPTY : new FilterRules(raw);
    }

    public Map<String, Object> asMap() {
        return raw;
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    public boolean shouldDrop(Map<String, Object> payload) {
        if (raw.isEmpty()) {
            return false;
        }

        if (raw.get("enabled") instanceof Boolean enabled && !enabled) {
            return false;
        }

        List<FilterCondition> dropIf = conditions("drop_if");
        if (dropIf != null) {
            for (FilterCondition condition : dropIf) {
                if (condition.matches(payload)) {
                    return true;
                }
            }
        }

        List<FilterCondition> passOnly = conditions("pass_only");
        if (passOnly != null && !passOnly.isEmpty()) {
            return passOnly.stream().noneMatch(condition -> condition.matches(payload));
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private List<FilterCondition> conditions(String key) {
        if (!(raw.get(key) instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .map(item -> FilterCondition.of((Map<String, Object>) item))
                .toList();
    }
}

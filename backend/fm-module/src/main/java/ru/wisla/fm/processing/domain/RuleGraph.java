package ru.wisla.fm.processing.domain;

import java.util.List;
import java.util.Map;

/**
 * The still-untyped node and edge maps of a rule canvas, as the rules adapter parsed them.
 * Keeping them as maps is what lets {@code domain} stay free of Jackson: the adapter owns the
 * JSON, the domain owns the interpretation ({@link RuleNode} / {@link RuleEdge}).
 */
public record RuleGraph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {

    public static RuleGraph empty() {
        return new RuleGraph(List.of(), List.of());
    }
}

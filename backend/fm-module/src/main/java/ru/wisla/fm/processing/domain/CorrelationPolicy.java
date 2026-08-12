package ru.wisla.fm.processing.domain;

/** How many events over which window a correlation rule groups, and which field they match on. */
public record CorrelationPolicy(int count, int windowMin, String matchField) {

    public static CorrelationPolicy fromNode(RuleNode node) {
        int count = parseInt(node.config().get("count"), 2);
        int windowMin = parseInt(node.config().get("windowMin"), 10);
        String matchField = node.config().getOrDefault("matchField", "title");
        return new CorrelationPolicy(count, windowMin, matchField);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}

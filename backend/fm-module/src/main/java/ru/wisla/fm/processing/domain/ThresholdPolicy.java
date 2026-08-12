package ru.wisla.fm.processing.domain;

/** How many critical events over which window a threshold rule fires on. */
public record ThresholdPolicy(int count, int windowMin) {

    public static ThresholdPolicy defaults() {
        return new ThresholdPolicy(5, 10);
    }

    public static ThresholdPolicy fromNode(RuleNode node) {
        int count = parseInt(node.config().get("count"), 5);
        int windowMin = parseInt(node.config().get("windowMin"), 10);
        return new ThresholdPolicy(count, windowMin);
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

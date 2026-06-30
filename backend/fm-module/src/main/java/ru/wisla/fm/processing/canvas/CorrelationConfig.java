package ru.wisla.fm.processing.canvas;

public record CorrelationConfig(int count, int windowMin, String matchField) {

    public static CorrelationConfig fromNode(CanvasNodeView node) {
        int count = parseInt(node.config().get("count"), 2);
        int windowMin = parseInt(node.config().get("windowMin"), 10);
        String matchField = node.config().getOrDefault("matchField", "title");
        return new CorrelationConfig(count, windowMin, matchField);
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

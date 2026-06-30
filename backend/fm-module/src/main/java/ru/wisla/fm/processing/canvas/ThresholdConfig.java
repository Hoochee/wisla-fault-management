package ru.wisla.fm.processing.canvas;

public record ThresholdConfig(int count, int windowMin) {

    public static ThresholdConfig defaults() {
        return new ThresholdConfig(5, 10);
    }

    public static ThresholdConfig fromNode(CanvasNodeView node) {
        int count = parseInt(node.config().get("count"), 5);
        int windowMin = parseInt(node.config().get("windowMin"), 10);
        return new ThresholdConfig(count, windowMin);
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

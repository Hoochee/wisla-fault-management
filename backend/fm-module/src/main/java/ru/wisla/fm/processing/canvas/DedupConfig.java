package ru.wisla.fm.processing.canvas;

public record DedupConfig(boolean useSource, boolean useTitle, boolean useCi) {

    public static DedupConfig defaults() {
        return new DedupConfig(true, true, true);
    }

    public static DedupConfig fromKey(String key) {
        if (key == null || key.isBlank()) {
            return defaults();
        }
        String normalized = key.toLowerCase();
        return new DedupConfig(
                normalized.contains("source"),
                normalized.contains("title"),
                normalized.contains("ci")
        );
    }
}

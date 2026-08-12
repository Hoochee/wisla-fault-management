package ru.wisla.fm.processing.domain;

/** Which keys a dedup rule matches on. Ported 1:1 from {@code canvas/DedupConfig}. */
public record DedupPolicy(boolean useSource, boolean useTitle, boolean useCi) {

    public static DedupPolicy defaults() {
        return new DedupPolicy(true, true, true);
    }

    public static DedupPolicy fromKey(String key) {
        if (key == null || key.isBlank()) {
            return defaults();
        }
        String normalized = key.toLowerCase();
        return new DedupPolicy(
                normalized.contains("source"),
                normalized.contains("title"),
                normalized.contains("ci")
        );
    }
}

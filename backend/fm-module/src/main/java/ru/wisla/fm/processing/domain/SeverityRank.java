package ru.wisla.fm.processing.domain;

/**
 * Severity ordering lifted verbatim from {@code DedupService.severityRank}: lower rank is more
 * severe, and anything unrecognised ranks last. A {@code null} severity throws, exactly as before.
 */
public final class SeverityRank {

    private SeverityRank() {
    }

    public static int of(String severity) {
        return switch (severity) {
            case "fatal" -> 0;
            case "critical" -> 1;
            case "major" -> 2;
            case "minor" -> 3;
            case "warning" -> 4;
            default -> 5;
        };
    }

    public static boolean isMoreSevere(String incoming, String current) {
        return of(incoming) < of(current);
    }
}

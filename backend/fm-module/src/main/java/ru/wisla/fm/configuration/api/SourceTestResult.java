package ru.wisla.fm.configuration.api;

import java.time.Instant;
import java.util.UUID;

public record SourceTestResult(
        boolean success,
        String message,
        Instant testedAt,
        UUID probeEventId,
        String delivery,
        Long latencyMs
) {
    public SourceTestResult(boolean success, String message, Instant testedAt, UUID probeEventId) {
        this(success, message, testedAt, probeEventId, null, null);
    }
}

package com.wisla.fm.adapter.ingest.domain;

import java.time.Instant;
import java.util.UUID;

public record PullMetricState(
        UUID sourceId,
        String externalId,
        String lastSeverity,
        Double lastValue,
        Instant updatedAt
) {
}

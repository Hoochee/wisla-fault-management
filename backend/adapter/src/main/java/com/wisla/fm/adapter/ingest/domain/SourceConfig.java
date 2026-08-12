package com.wisla.fm.adapter.ingest.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Locally cached configuration of an event source, owned by fm-module and synchronized over HTTP.
 */
public record SourceConfig(
        UUID sourceId,
        String sourceKey,
        String apiKeyHash,
        String endpoint,
        FilterRules filterRules,
        boolean blocked,
        Instant ttlExpiresAt,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean isExpired(Clock clock) {
        return ttlExpiresAt.isBefore(clock.instant());
    }
}

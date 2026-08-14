package com.wisla.fm.adapter.ingest.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
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
        Instant updatedAt,
        String type,
        String schedule,
        Map<String, Object> parserConfig
) {

    public static final String TYPE_PUSH_REST = "push_rest";
    public static final String TYPE_PULL_ETL = "pull_etl";

    public SourceConfig {
        if (type == null || type.isBlank()) {
            type = TYPE_PUSH_REST;
        }
        if (parserConfig == null) {
            parserConfig = Map.of();
        }
    }

    public SourceConfig(
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
        this(
                sourceId,
                sourceKey,
                apiKeyHash,
                endpoint,
                filterRules,
                blocked,
                ttlExpiresAt,
                createdAt,
                updatedAt,
                TYPE_PUSH_REST,
                null,
                Map.of()
        );
    }

    public boolean isExpired(Clock clock) {
        return ttlExpiresAt.isBefore(clock.instant());
    }

    public boolean isPullEtl() {
        return TYPE_PULL_ETL.equals(type);
    }
}

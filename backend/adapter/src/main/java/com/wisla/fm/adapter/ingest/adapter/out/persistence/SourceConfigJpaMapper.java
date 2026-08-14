package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import com.wisla.fm.adapter.ingest.domain.FilterRules;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between the {@code source_config_snapshots} row and the domain model.
 */
@Component
public class SourceConfigJpaMapper {

    public SourceConfig toDomain(SourceConfigSnapshotJpaEntity entity) {
        return new SourceConfig(
                entity.getSourceId(),
                entity.getSourceKey(),
                entity.getApiKeyHash(),
                entity.getEndpoint(),
                FilterRules.of(entity.getFilterRules()),
                entity.isBlocked(),
                entity.getTtlExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSourceType(),
                entity.getSchedule(),
                entity.getParserConfig()
        );
    }

    /**
     * Applies the domain state onto an existing or freshly created row, leaving {@code created_at}
     * of an already stored snapshot untouched.
     */
    public void applyTo(SourceConfigSnapshotJpaEntity entity, SourceConfig config) {
        entity.replace(
                config.sourceId(),
                config.sourceKey(),
                config.apiKeyHash(),
                config.endpoint(),
                config.filterRules().asMap(),
                config.blocked(),
                config.ttlExpiresAt(),
                config.updatedAt(),
                config.type(),
                config.schedule(),
                config.parserConfig()
        );
    }
}

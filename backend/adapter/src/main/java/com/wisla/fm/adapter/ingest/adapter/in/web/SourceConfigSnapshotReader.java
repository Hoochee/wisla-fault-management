package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.wisla.fm.adapter.ingest.adapter.in.web.dto.SourceConfigSnapshotDto;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.domain.IngestRejection;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Resolves the locally cached snapshot the internal endpoints expose, and renders it as
 * {@link SourceConfigSnapshotDto}. Both used to live on {@code SourceConfigService}.
 */
@Component
public class SourceConfigSnapshotReader {

    private final SourceConfigLookupPort sourceConfigLookup;
    private final Clock clock;

    public SourceConfigSnapshotReader(SourceConfigLookupPort sourceConfigLookup, Clock clock) {
        this.sourceConfigLookup = sourceConfigLookup;
        this.clock = clock;
    }

    public SourceConfig requireBySourceId(UUID sourceId) {
        return sourceConfigLookup.findBySourceId(sourceId)
                .filter(config -> !config.isExpired(clock))
                .orElseThrow(() -> new IngestRejection(
                        "config_not_found",
                        "Source configuration snapshot not found or expired",
                        404
                ));
    }

    public SourceConfigSnapshotDto toDto(SourceConfig config) {
        return new SourceConfigSnapshotDto(
                config.sourceId(),
                config.sourceKey(),
                config.filterRules().asMap(),
                config.apiKeyHash(),
                config.endpoint(),
                config.blocked(),
                config.ttlExpiresAt(),
                config.updatedAt()
        );
    }
}

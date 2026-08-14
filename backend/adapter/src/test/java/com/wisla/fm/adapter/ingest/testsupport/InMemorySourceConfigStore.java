package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigStorePort;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-written double for the two source-configuration ports, reproducing the upsert rule that
 * {@code createdAt} of an existing snapshot survives.
 */
public final class InMemorySourceConfigStore implements SourceConfigLookupPort, SourceConfigStorePort {

    private final Map<UUID, SourceConfig> bySourceId = new LinkedHashMap<>();

    public void put(SourceConfig config) {
        bySourceId.put(config.sourceId(), config);
    }

    public List<SourceConfig> all() {
        return List.copyOf(bySourceId.values());
    }

    @Override
    public List<SourceConfig> findAll() {
        return all();
    }

    @Override
    public Optional<SourceConfig> findBySourceKey(String sourceKey) {
        return bySourceId.values().stream()
                .filter(config -> config.sourceKey().equals(sourceKey))
                .findFirst();
    }

    @Override
    public Optional<SourceConfig> findBySourceId(UUID sourceId) {
        return Optional.ofNullable(bySourceId.get(sourceId));
    }

    @Override
    public void upsert(SourceConfig config) {
        SourceConfig existing = bySourceId.get(config.sourceId());
        SourceConfig stored = existing == null
                ? config
                : new SourceConfig(
                        config.sourceId(),
                        config.sourceKey(),
                        config.apiKeyHash(),
                        config.endpoint(),
                        config.filterRules(),
                        config.blocked(),
                        config.ttlExpiresAt(),
                        existing.createdAt(),
                        config.updatedAt(),
                        config.type(),
                        config.schedule(),
                        config.parserConfig()
                );
        bySourceId.put(stored.sourceId(), stored);
    }
}

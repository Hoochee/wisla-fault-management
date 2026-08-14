package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigStorePort;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SourceConfigPersistenceAdapter implements SourceConfigLookupPort, SourceConfigStorePort {

    private final SourceConfigSnapshotJpaRepository repository;
    private final SourceConfigJpaMapper mapper;

    public SourceConfigPersistenceAdapter(
            SourceConfigSnapshotJpaRepository repository,
            SourceConfigJpaMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<SourceConfig> findBySourceKey(String sourceKey) {
        return repository.findBySourceKey(sourceKey).map(mapper::toDomain);
    }

    @Override
    public Optional<SourceConfig> findBySourceId(UUID sourceId) {
        return repository.findById(sourceId).map(mapper::toDomain);
    }

    @Override
    public List<SourceConfig> findAll() {
        return repository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void upsert(SourceConfig config) {
        SourceConfigSnapshotJpaEntity entity = repository.findById(config.sourceId())
                .orElseGet(SourceConfigSnapshotJpaEntity::createEmpty);
        mapper.applyTo(entity, config);
        repository.save(entity);
    }
}

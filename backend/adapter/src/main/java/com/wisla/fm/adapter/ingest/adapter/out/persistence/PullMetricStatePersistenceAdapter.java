package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import com.wisla.fm.adapter.ingest.application.port.out.PullMetricStateStorePort;
import com.wisla.fm.adapter.ingest.domain.PullMetricState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class PullMetricStatePersistenceAdapter implements PullMetricStateStorePort {

    private final PullMetricStateJpaRepository repository;

    public PullMetricStatePersistenceAdapter(PullMetricStateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PullMetricState> find(UUID sourceId, String externalId) {
        return repository.findById(new PullMetricStateJpaEntity.Pk(sourceId, externalId))
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public void upsert(PullMetricState state) {
        PullMetricStateJpaEntity.Pk pk = new PullMetricStateJpaEntity.Pk(state.sourceId(), state.externalId());
        PullMetricStateJpaEntity entity = repository.findById(pk).orElseGet(() -> new PullMetricStateJpaEntity(
                state.sourceId(),
                state.externalId(),
                state.lastSeverity(),
                state.lastValue(),
                state.updatedAt()
        ));
        entity.apply(state.lastSeverity(), state.lastValue(), state.updatedAt());
        repository.save(entity);
    }

    private PullMetricState toDomain(PullMetricStateJpaEntity entity) {
        return new PullMetricState(
                entity.getSourceId(),
                entity.getExternalId(),
                entity.getLastSeverity(),
                entity.getLastValue(),
                entity.getUpdatedAt()
        );
    }
}

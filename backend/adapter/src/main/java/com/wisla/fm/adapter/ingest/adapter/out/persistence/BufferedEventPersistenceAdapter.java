package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import com.wisla.fm.adapter.ingest.application.port.out.BufferedEventStorePort;
import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class BufferedEventPersistenceAdapter implements BufferedEventStorePort {

    private final BufferedMessageJpaRepository repository;
    private final BufferedEventJpaMapper mapper;

    public BufferedEventPersistenceAdapter(
            BufferedMessageJpaRepository repository,
            BufferedEventJpaMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public BufferedEvent save(BufferedEvent event) {
        repository.save(mapper.toEntity(event));
        return event;
    }

    @Override
    public List<BufferedEvent> findDue(Instant now) {
        return repository.findReadyForRetry(now).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(BufferedEvent event) {
        repository.deleteById(event.id());
    }

    @Override
    public long count() {
        return repository.count();
    }
}

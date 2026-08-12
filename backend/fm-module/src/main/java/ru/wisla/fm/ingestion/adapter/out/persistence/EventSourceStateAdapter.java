package ru.wisla.fm.ingestion.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Delegates to the {@code configuration} context, which owns the {@code event_sources} mapping.
 */
@Component
public class EventSourceStateAdapter implements EventSourceStatePort {

    private final EventSourceRepository eventSourceRepository;

    public EventSourceStateAdapter(EventSourceRepository eventSourceRepository) {
        this.eventSourceRepository = eventSourceRepository;
    }

    @Override
    public Optional<SourceIngestState> find(UUID sourceId) {
        return eventSourceRepository.findById(sourceId)
                .map(source -> new SourceIngestState(source.getId(), source.getName(), source.getStatus()));
    }

    @Override
    public void markSuccess(UUID sourceId, String adapterVersion, Instant at) {
        eventSourceRepository.findById(sourceId).ifPresent(source -> {
            source.setLastSuccessAt(at);
            if (adapterVersion != null) {
                source.setAdapterVersion(adapterVersion);
            }
            eventSourceRepository.save(source);
        });
    }
}

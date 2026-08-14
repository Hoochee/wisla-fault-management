package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ru.wisla.fm.health.application.port.out.ActiveSignalsPort;
import ru.wisla.fm.health.domain.ActiveSignal;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaEntity;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class ActiveSignalsPersistenceAdapter implements ActiveSignalsPort {

    private final EventJpaRepository eventJpaRepository;

    public ActiveSignalsPersistenceAdapter(EventJpaRepository eventJpaRepository) {
        this.eventJpaRepository = eventJpaRepository;
    }

    @Override
    public List<ActiveSignal> findByCiIds(Collection<UUID> ciIds) {
        if (ciIds == null || ciIds.isEmpty()) {
            return List.of();
        }
        return eventJpaRepository.findActiveByCiIds(List.copyOf(ciIds)).stream()
                .map(this::toSignal)
                .toList();
    }

    private ActiveSignal toSignal(EventJpaEntity event) {
        return new ActiveSignal(event.getId(), event.getCiId(), event.getSeverity(), event.getTitle());
    }
}

package ru.wisla.fm.processing.adapter.out.ingestion;

import org.springframework.stereotype.Component;
import ru.wisla.fm.ingestion.adapter.out.persistence.RawEventJpaEntity;
import ru.wisla.fm.ingestion.adapter.out.persistence.RawEventJpaRepository;
import ru.wisla.fm.processing.application.port.out.RawEventStatePort;
import ru.wisla.fm.processing.domain.IncomingRawEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and updates {@code raw_events} through the repository {@code ingestion} owns, rather than
 * mapping the table a second time — two Hibernate mappings of one table inside one transaction is the
 * hazard design decision D1 rules out.
 */
@Component
public class RawEventStateAdapter implements RawEventStatePort {

    private final RawEventJpaRepository rawEventJpaRepository;

    public RawEventStateAdapter(RawEventJpaRepository rawEventJpaRepository) {
        this.rawEventJpaRepository = rawEventJpaRepository;
    }

    @Override
    public Optional<IncomingRawEvent> findById(UUID rawEventId) {
        return rawEventJpaRepository.findById(rawEventId).map(RawEventStateAdapter::toDomain);
    }

    @Override
    public void markProcessed(UUID rawEventId, UUID eventId, UUID ciId) {
        rawEventJpaRepository.findById(rawEventId).ifPresent(raw -> {
            bindCi(raw, ciId);
            raw.setProcessed(true);
            raw.setProcessedEventId(eventId);
            rawEventJpaRepository.save(raw);
        });
    }

    @Override
    public void recordError(UUID rawEventId, UUID ciId, String message) {
        rawEventJpaRepository.findById(rawEventId).ifPresent(raw -> {
            bindCi(raw, ciId);
            raw.setProcessingError(message);
            rawEventJpaRepository.save(raw);
        });
    }

    private static void bindCi(RawEventJpaEntity raw, UUID ciId) {
        if (ciId != null) {
            raw.setCiId(ciId);
        }
    }

    private static IncomingRawEvent toDomain(RawEventJpaEntity raw) {
        return new IncomingRawEvent(
                raw.getId(),
                raw.getSourceId(),
                raw.getExternalId(),
                raw.getTitle(),
                raw.getDescription(),
                raw.getSeverity(),
                raw.getStatus(),
                raw.getNodeFqdn(),
                raw.getCiId(),
                raw.getPayload(),
                raw.getSourceAt(),
                raw.isProcessed());
    }
}

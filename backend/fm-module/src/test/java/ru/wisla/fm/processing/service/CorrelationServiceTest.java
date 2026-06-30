package ru.wisla.fm.processing.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.wisla.fm.processing.canvas.CorrelationConfig;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationServiceTest extends AbstractFmModuleTest {

    @Autowired private CorrelationService correlationService;
    @Autowired private EventRepository eventRepository;

    @Test
    void linksSecondEventToRootWithinWindow() {
        UUID sourceId = UUID.randomUUID();
        UUID ciId = UUID.randomUUID();
        CorrelationConfig config = new CorrelationConfig(2, 10, "title");

        EventEntity first = saveEvent(sourceId, ciId, "Correlated alert");
        EventEntity second = saveEvent(sourceId, ciId, "Correlated alert");

        correlationService.evaluateAfterProcessing(first, config);
        correlationService.evaluateAfterProcessing(second, config);

        EventEntity updatedSecond = eventRepository.findById(second.getId()).orElseThrow();
        assertThat(updatedSecond.getRootEventId()).isEqualTo(first.getId());
    }

    private EventEntity saveEvent(UUID sourceId, UUID ciId, String title) {
        EventEntity event = new EventEntity();
        event.setStatus("new");
        event.setSeverity("major");
        event.setTitle(title);
        event.setSourceId(sourceId);
        event.setCiId(ciId);
        event.setSourceAt(Instant.now());
        return eventRepository.save(event);
    }
}

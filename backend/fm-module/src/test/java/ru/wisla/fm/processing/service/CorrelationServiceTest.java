package ru.wisla.fm.processing.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.service.CorrelationEvaluator;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationServiceTest extends AbstractFmModuleTest {

    private final CorrelationEvaluator correlationEvaluator = new CorrelationEvaluator();

    @Autowired private EventStorePort eventStore;

    @Test
    void linksSecondEventToRootWithinWindow() {
        UUID sourceId = UUID.randomUUID();
        UUID ciId = UUID.randomUUID();
        CorrelationPolicy config = new CorrelationPolicy(2, 10, "title");

        Event first = saveEvent(sourceId, ciId, "Correlated alert");
        Event second = saveEvent(sourceId, ciId, "Correlated alert");

        applyCorrelation(first, config);
        applyCorrelation(second, config);

        Event updatedSecond = eventStore.findById(second.getId()).orElseThrow();
        assertThat(updatedSecond.getRootEventId()).isEqualTo(first.getId());
    }

    /** The correlation step of {@code ProcessRawEventBatchService}, against the real adapter. */
    private void applyCorrelation(Event processedEvent, CorrelationPolicy policy) {
        CorrelationEvaluator.Window window = new CorrelationEvaluator.Window() {

            @Override
            public List<Event> findWindow(Event event, String matchField, Instant since) {
                return eventStore.findWindow(event, matchField, since);
            }

            @Override
            public Optional<Event> findById(UUID id) {
                return eventStore.findById(id);
            }
        };
        if (correlationEvaluator.evaluate(processedEvent, policy, Instant.now(), window)) {
            eventStore.save(processedEvent);
        }
    }

    private Event saveEvent(UUID sourceId, UUID ciId, String title) {
        Event event = new Event();
        event.setStatus("new");
        event.setSeverity("major");
        event.setTitle(title);
        event.setSourceId(sourceId);
        event.setCiId(ciId);
        event.setSourceAt(Instant.now());
        return eventStore.save(event);
    }
}

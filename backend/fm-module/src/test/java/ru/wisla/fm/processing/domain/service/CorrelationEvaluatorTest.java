package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.Event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the assertions pinned against the current {@code CorrelationService} by
 * {@code CorrelationServiceCharacterizationTest} (task 1.6): root selection, transitive root
 * adoption, the self-reference guard and the window arithmetic.
 *
 * <p>Choosing between the six derived window queries stays a persistence concern (design decision
 * D4), so this test pins that the configured {@code matchField} — including an unrecognised one — is
 * handed to {@link CorrelationEvaluator.Window} verbatim. That an unrecognised value resolves to the
 * title window is asserted against the query layer by the group-1 characterization test.
 */
class CorrelationEvaluatorTest {

    private static final UUID SOURCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CI_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant NOW = Instant.parse("2026-02-03T08:15:00Z");

    private final CorrelationEvaluator evaluator = new CorrelationEvaluator();

    // --- window lookup -------------------------------------------------------------------------

    @Test
    void windowLookupReceivesTheProcessedEventTheMatchFieldAndTheWindowStart() {
        RecordingWindow window = new RecordingWindow();
        Event processed = persisted("Interface down", "major", CI_ID);

        evaluator.evaluate(processed, new CorrelationPolicy(2, 15, "title"), NOW, window);

        assertThat(window.windowEvent).isSameAs(processed);
        assertThat(window.windowMatchField).isEqualTo("title");
        assertThat(window.windowSince).isEqualTo(NOW.minus(15, ChronoUnit.MINUTES));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "severity", "source", "node", "ci", "", "NODE_FQDN"})
    void theConfiguredMatchFieldIsPassedThroughUntouched(String matchField) {
        RecordingWindow window = new RecordingWindow();

        evaluator.evaluate(
                persisted("Interface down", "major", CI_ID),
                new CorrelationPolicy(2, 10, matchField),
                NOW,
                window);

        assertThat(window.windowMatchField).isEqualTo(matchField);
    }

    @Test
    void aWindowSmallerThanTheConfiguredCountDoesNothing() {
        RecordingWindow window = new RecordingWindow();
        Event processed = persisted("Interface down", "major", CI_ID);

        boolean correlated = evaluator.evaluate(processed, new CorrelationPolicy(2, 10, "title"), NOW, window);

        assertThat(correlated).isFalse();
        assertThat(processed.getRootEventId()).isNull();
        assertThat(window.calls).containsExactly("findWindow");
    }

    // --- root selection ------------------------------------------------------------------------

    @Test
    void theOldestEventInTheWindowBecomesTheRoot() {
        Event first = persisted("Link flapping", "major", CI_ID);
        Event processed = persisted("Link flapping", "major", CI_ID);
        RecordingWindow window = new RecordingWindow().withWindow(List.of(first, processed));

        boolean correlated = evaluator.evaluate(processed, new CorrelationPolicy(2, 10, "title"), NOW, window);

        assertThat(correlated).isTrue();
        assertThat(processed.getRootEventId()).isEqualTo(first.getId());
    }

    @Test
    void anEventThatIsItsOwnWindowRootIsNotLinkedToItself() {
        Event first = persisted("Link flapping", "major", CI_ID);
        Event second = persisted("Link flapping", "major", CI_ID);
        RecordingWindow window = new RecordingWindow().withWindow(List.of(first, second));

        boolean correlated = evaluator.evaluate(first, new CorrelationPolicy(2, 10, "title"), NOW, window);

        assertThat(correlated).isFalse();
        assertThat(first.getRootEventId()).isNull();
    }

    /** When the oldest window event already points at a root, that root is adopted transitively. */
    @Test
    void anExistingRootEventIdOnTheWindowRootIsAdopted() {
        Event originalRoot = persisted("Original root", "major", CI_ID);
        Event windowRoot = persisted("Link flapping", "major", CI_ID);
        windowRoot.assignRoot(originalRoot.getId());
        Event processed = persisted("Link flapping", "major", CI_ID);
        RecordingWindow window = new RecordingWindow()
                .withWindow(List.of(windowRoot, processed))
                .withById(originalRoot);

        boolean correlated = evaluator.evaluate(processed, new CorrelationPolicy(2, 10, "title"), NOW, window);

        assertThat(correlated).isTrue();
        assertThat(processed.getRootEventId()).isEqualTo(originalRoot.getId());
        assertThat(window.findByIdArgument).isEqualTo(originalRoot.getId());
    }

    /** An unresolvable root pointer degrades to the window root itself rather than failing. */
    @Test
    void anUnresolvableRootPointerFallsBackToTheWindowRoot() {
        Event windowRoot = persisted("Link flapping", "major", CI_ID);
        windowRoot.assignRoot(UUID.randomUUID());
        Event processed = persisted("Link flapping", "major", CI_ID);
        RecordingWindow window = new RecordingWindow().withWindow(List.of(windowRoot, processed));

        boolean correlated = evaluator.evaluate(processed, new CorrelationPolicy(2, 10, "title"), NOW, window);

        assertThat(correlated).isTrue();
        assertThat(processed.getRootEventId()).isEqualTo(windowRoot.getId());
    }

    private static Event persisted(String title, String severity, UUID ciId) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setStatus("new");
        event.setSeverity(severity);
        event.setTitle(title);
        event.setSourceId(SOURCE_ID);
        event.setCiId(ciId);
        event.setSourceAt(Instant.parse("2026-01-01T10:00:00Z"));
        return event;
    }

    private static final class RecordingWindow implements CorrelationEvaluator.Window {

        private final List<String> calls = new ArrayList<>();
        private List<Event> windowEvents = List.of();
        private Event byId;

        private Event windowEvent;
        private String windowMatchField;
        private Instant windowSince;
        private UUID findByIdArgument;

        RecordingWindow withWindow(List<Event> events) {
            this.windowEvents = events;
            return this;
        }

        RecordingWindow withById(Event event) {
            this.byId = event;
            return this;
        }

        @Override
        public List<Event> findWindow(Event processedEvent, String matchField, Instant since) {
            calls.add("findWindow");
            windowEvent = processedEvent;
            windowMatchField = matchField;
            windowSince = since;
            return windowEvents;
        }

        @Override
        public Optional<Event> findById(UUID id) {
            calls.add("findById");
            findByIdArgument = id;
            return byId != null && byId.getId().equals(id) ? Optional.of(byId) : Optional.empty();
        }
    }
}

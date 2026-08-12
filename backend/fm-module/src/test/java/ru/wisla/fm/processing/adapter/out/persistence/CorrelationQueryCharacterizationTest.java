package ru.wisla.fm.processing.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.service.CorrelationEvaluator;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test carried over from {@code service/CorrelationServiceCharacterizationTest}: the
 * same six window query variants plus root selection, root adoption and the self-reference guard, now
 * driving {@link CorrelationEvaluator} over {@link EventPersistenceAdapter}.
 *
 * <p>Query selection is pinned against a recording repository proxy; root selection needs persisted
 * identifiers and {@code created_at} ordering, so it runs against H2 like the existing
 * {@code CorrelationServiceTest}.
 */
class CorrelationQueryCharacterizationTest extends AbstractFmModuleTest {

    private static final List<String> ACTIVE_STATUSES = List.of("new", "in_progress", "maintenance", "deferred");

    private static final String TITLE_WITH_CI =
            "findBySourceIdAndCiIdAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc";
    private static final String SEVERITY_CI_IS_NULL =
            "findBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc";
    private static final String SOURCE_CI_IS_NULL =
            "findBySourceIdAndCiIdIsNullAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc";

    private static final UUID PROXY_SOURCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PROXY_CI_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private final CorrelationEvaluator correlationEvaluator = new CorrelationEvaluator();

    @Autowired private EventStorePort eventStore;

    // --- the six window query variants ---------------------------------------------------------

    @ParameterizedTest(name = "matchField={0} ciPresent={1} -> {2}")
    @CsvSource({
            "title,    true,  findBySourceIdAndCiIdAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc",
            "title,    false, findBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc",
            "severity, true,  findBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc",
            "severity, false, findBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc",
            "source,   true,  findBySourceIdAndCiIdAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc",
            "source,   false, findBySourceIdAndCiIdIsNullAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc"
    })
    void resolvesToTheExpectedWindowQuery(String matchField, boolean ciPresent, String expectedQuery) {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        applyCorrelation(repository, proxyEvent(ciPresent ? PROXY_CI_ID : null),
                new CorrelationPolicy(2, 10, matchField));

        assertThat(repository.methodNames()).containsExactly(expectedQuery);
    }

    /** Any unrecognised matchField falls through to the title-based window. */
    @ParameterizedTest
    @CsvSource({"node", "ci", "'' ", "NODE_FQDN"})
    void unknownMatchFieldFallsBackToTheTitleWindow(String matchField) {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        applyCorrelation(repository, proxyEvent(PROXY_CI_ID), new CorrelationPolicy(2, 10, matchField));

        assertThat(repository.methodNames()).containsExactly(TITLE_WITH_CI);
    }

    @Test
    void windowQueryArgumentsAreTheEventsOwnKeysAndTheActiveStatuses() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        Instant before = Instant.now();
        applyCorrelation(repository, proxyEvent(PROXY_CI_ID), new CorrelationPolicy(2, 15, "title"));
        Instant after = Instant.now();

        List<Object> args = repository.callTo(TITLE_WITH_CI).args();
        assertThat(args.get(0)).isEqualTo(PROXY_SOURCE_ID);
        assertThat(args.get(1)).isEqualTo(PROXY_CI_ID);
        assertThat(args.get(2)).isEqualTo("Interface down");
        assertThat((Instant) args.get(3)).isBetween(
                before.minus(15, ChronoUnit.MINUTES), after.minus(15, ChronoUnit.MINUTES));
        assertThat(args.get(4)).isEqualTo(ACTIVE_STATUSES);
    }

    @Test
    void severityWindowMatchesOnTheEventsSeverity() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        applyCorrelation(repository, proxyEvent(null), new CorrelationPolicy(2, 10, "severity"));

        List<Object> args = repository.callTo(SEVERITY_CI_IS_NULL).args();
        assertThat(args.get(0)).isEqualTo(PROXY_SOURCE_ID);
        assertThat(args.get(1)).isEqualTo("major");
        assertThat(args.get(3)).isEqualTo(ACTIVE_STATUSES);
    }

    @Test
    void sourceWindowMatchesOnSourceAndCiOnly() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        applyCorrelation(repository, proxyEvent(null), new CorrelationPolicy(2, 10, "source"));

        List<Object> args = repository.callTo(SOURCE_CI_IS_NULL).args();
        assertThat(args).hasSize(3);
        assertThat(args.get(0)).isEqualTo(PROXY_SOURCE_ID);
        assertThat(args.get(2)).isEqualTo(ACTIVE_STATUSES);
    }

    @Test
    void aWindowSmallerThanTheConfiguredCountDoesNothing() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        applyCorrelation(repository, proxyEvent(PROXY_CI_ID), new CorrelationPolicy(2, 10, "title"));

        assertThat(repository.methodNames()).doesNotContain("save", "findById");
    }

    // --- root selection against real identifiers -----------------------------------------------

    @Test
    void theOldestEventInTheWindowBecomesTheRoot() throws InterruptedException {
        UUID sourceId = UUID.randomUUID();
        UUID ciId = UUID.randomUUID();
        Event first = save(sourceId, ciId, "Link flapping", "major");
        Thread.sleep(5);
        Event second = save(sourceId, ciId, "Link flapping", "major");

        applyCorrelation(second, new CorrelationPolicy(2, 10, "title"));

        assertThat(reload(second).getRootEventId()).isEqualTo(first.getId());
    }

    @Test
    void anEventThatIsItsOwnWindowRootIsNotLinkedToItself() throws InterruptedException {
        UUID sourceId = UUID.randomUUID();
        UUID ciId = UUID.randomUUID();
        Event first = save(sourceId, ciId, "Link flapping", "major");
        Thread.sleep(5);
        save(sourceId, ciId, "Link flapping", "major");

        applyCorrelation(first, new CorrelationPolicy(2, 10, "title"));

        assertThat(reload(first).getRootEventId()).isNull();
    }

    /** When the oldest window event already points at a root, that root is adopted transitively. */
    @Test
    void anExistingRootEventIdOnTheWindowRootIsAdopted() throws InterruptedException {
        UUID sourceId = UUID.randomUUID();
        UUID ciId = UUID.randomUUID();
        Event originalRoot = save(sourceId, ciId, "Original root", "major");
        Thread.sleep(5);
        Event windowRoot = save(sourceId, ciId, "Link flapping", "major");
        windowRoot.setRootEventId(originalRoot.getId());
        eventStore.save(windowRoot);
        Thread.sleep(5);
        Event processed = save(sourceId, ciId, "Link flapping", "major");

        applyCorrelation(processed, new CorrelationPolicy(2, 10, "title"));

        assertThat(reload(processed).getRootEventId()).isEqualTo(originalRoot.getId());
    }

    @Test
    void aLoneEventIsNotCorrelated() {
        UUID sourceId = UUID.randomUUID();
        UUID ciId = UUID.randomUUID();
        Event only = save(sourceId, ciId, "Solitary alert", "major");

        applyCorrelation(only, new CorrelationPolicy(2, 10, "title"));

        assertThat(reload(only).getRootEventId()).isNull();
    }

    /** The correlation step of {@code ProcessRawEventBatchService}, against the real adapter. */
    private void applyCorrelation(Event processedEvent, CorrelationPolicy policy) {
        applyCorrelation(eventStore, processedEvent, policy);
    }

    private void applyCorrelation(CharacterizationEventRepository repository,
                                  Event processedEvent,
                                  CorrelationPolicy policy) {
        applyCorrelation(new EventPersistenceAdapter(repository.asRepository(), new EventJpaMapper()),
                processedEvent, policy);
    }

    private void applyCorrelation(EventStorePort store, Event processedEvent, CorrelationPolicy policy) {
        CorrelationEvaluator.Window window = new CorrelationEvaluator.Window() {

            @Override
            public List<Event> findWindow(Event event, String matchField, Instant since) {
                return store.findWindow(event, matchField, since);
            }

            @Override
            public Optional<Event> findById(UUID id) {
                return store.findById(id);
            }
        };
        if (correlationEvaluator.evaluate(processedEvent, policy, Instant.now(), window)) {
            store.save(processedEvent);
        }
    }

    private Event save(UUID sourceId, UUID ciId, String title, String severity) {
        Event event = new Event();
        event.setStatus("new");
        event.setSeverity(severity);
        event.setTitle(title);
        event.setSourceId(sourceId);
        event.setCiId(ciId);
        event.setSourceAt(Instant.now());
        return eventStore.save(event);
    }

    private Event reload(Event event) {
        return eventStore.findById(event.getId()).orElseThrow();
    }

    private static Event proxyEvent(UUID ciId) {
        Event event = new Event();
        event.setStatus("new");
        event.setSeverity("major");
        event.setTitle("Interface down");
        event.setSourceId(PROXY_SOURCE_ID);
        event.setCiId(ciId);
        event.setSourceAt(Instant.parse("2026-01-01T10:00:00Z"));
        return event;
    }
}

package ru.wisla.fm.processing.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.ThresholdPolicy;
import ru.wisla.fm.processing.domain.service.ThresholdEvaluator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test carried over from {@code service/ThresholdServiceCharacterizationTest}: the
 * same four count/exists query variants, synthetic event and title-based idempotency, now driving
 * {@link ThresholdEvaluator} over {@link EventPersistenceAdapter} through the same window the use
 * case installs.
 *
 * <p>One case from the original has no counterpart here: {@code evaluateAfterProcessing(event, false)}
 * returned before touching the repository, and a disabled threshold is now simply the absence of a
 * threshold intent — pinned by {@code ProcessRawEventBatchServiceTest}.
 */
class ThresholdQueryCharacterizationTest {

    private static final List<String> ACTIVE_STATUSES = List.of("new", "in_progress", "maintenance", "deferred");

    private static final String COUNT_WITH_CI = "countBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusIn";
    private static final String COUNT_CI_IS_NULL = "countBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusIn";
    private static final String EXISTS_WITH_CI = "existsBySourceIdAndCiIdAndTitleAndCreatedAtAfter";
    private static final String EXISTS_CI_IS_NULL = "existsBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfter";

    private static final UUID SOURCE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CI_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final ThresholdEvaluator thresholdEvaluator = new ThresholdEvaluator();

    // --- entry conditions ----------------------------------------------------------------------

    /** Only the literal severity "critical" counts — "fatal" is more severe but does not qualify. */
    @ParameterizedTest
    @ValueSource(strings = {"fatal", "major", "minor", "warning", "normal"})
    void nonCriticalTriggerSeverityIsIgnored(String severity) {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        applyThreshold(repository, trigger(severity, CI_ID), new ThresholdPolicy(5, 10));

        assertThat(repository.methodNames()).isEmpty();
    }

    // --- the four count / exists query variants ------------------------------------------------

    @Test
    void countQueryUsesTheCiVariantWhenTheTriggerHasACi() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 5L);

        Instant before = Instant.now();
        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(5, 10));
        Instant after = Instant.now();

        assertThat(repository.methodNames()).doesNotContain(COUNT_CI_IS_NULL, EXISTS_CI_IS_NULL);
        List<Object> args = repository.callTo(COUNT_WITH_CI).args();
        assertThat(args.get(0)).isEqualTo(SOURCE_ID);
        assertThat(args.get(1)).isEqualTo(CI_ID);
        assertThat(args.get(2)).isEqualTo("critical");
        assertThat((Instant) args.get(3)).isBetween(minutesBefore(before, 10), minutesBefore(after, 10));
        assertThat(args.get(4)).isEqualTo(ACTIVE_STATUSES);
    }

    @Test
    void countQueryUsesTheCiIsNullVariantWhenTheTriggerHasNoCi() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_CI_IS_NULL, 5L);

        applyThreshold(repository, trigger("critical", null), new ThresholdPolicy(5, 10));

        assertThat(repository.methodNames()).doesNotContain(COUNT_WITH_CI, EXISTS_WITH_CI);
        List<Object> args = repository.callTo(COUNT_CI_IS_NULL).args();
        assertThat(args.get(0)).isEqualTo(SOURCE_ID);
        assertThat(args.get(1)).isEqualTo("critical");
        assertThat(args.get(3)).isEqualTo(ACTIVE_STATUSES);
    }

    @Test
    void existsQueryUsesTheCiVariantAndTheSameWindowStartAsTheCountQuery() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 5L);

        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(5, 10));

        List<Object> existsArgs = repository.callTo(EXISTS_WITH_CI).args();
        assertThat(existsArgs).containsExactly(
                SOURCE_ID,
                CI_ID,
                "Threshold: 5+ critical events in 10 minutes",
                repository.callTo(COUNT_WITH_CI).args().get(3)
        );
    }

    @Test
    void existsQueryUsesTheCiIsNullVariantWhenTheTriggerHasNoCi() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_CI_IS_NULL, 5L);

        applyThreshold(repository, trigger("critical", null), new ThresholdPolicy(5, 10));

        assertThat(repository.callTo(EXISTS_CI_IS_NULL).args()).containsExactly(
                SOURCE_ID,
                "Threshold: 5+ critical events in 10 minutes",
                repository.callTo(COUNT_CI_IS_NULL).args().get(2)
        );
    }

    // --- threshold arithmetic and idempotency --------------------------------------------------

    @Test
    void countBelowTheThresholdStopsBeforeTheExistsQuery() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 4L);

        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(5, 10));

        assertThat(repository.methodNames()).containsExactly(COUNT_WITH_CI);
    }

    @Test
    void countEqualToTheThresholdAlreadyFires() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 5L);

        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(5, 10));

        assertThat(repository.methodNames()).containsExactly(COUNT_WITH_CI, EXISTS_WITH_CI, "save");
    }

    @Test
    void anExistingSyntheticWithTheSameTitleInTheWindowSuppressesANewOne() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 99L)
                .stub(EXISTS_WITH_CI, true);

        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(5, 10));

        assertThat(repository.methodNames()).containsExactly(COUNT_WITH_CI, EXISTS_WITH_CI);
    }

    // --- the synthetic event -------------------------------------------------------------------

    @Test
    void syntheticEventCarriesTheFrozenTitleSeverityAndAttributes() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 5L);

        Instant before = Instant.now();
        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(5, 10));
        Instant after = Instant.now();

        EventJpaEntity synthetic = savedEntity(repository);
        assertThat(synthetic.getStatus()).isEqualTo("new");
        assertThat(synthetic.getSeverity()).isEqualTo("fatal");
        assertThat(synthetic.getTitle()).isEqualTo("Threshold: 5+ critical events in 10 minutes");
        assertThat(synthetic.getDescription())
                .isEqualTo("Auto-generated by threshold rule after 5 critical events within 10 minutes");
        assertThat(synthetic.getAttributes()).isEqualTo("{\"synthetic\":true,\"ruleType\":\"threshold\"}");
        assertThat(synthetic.getTags()).isEqualTo("[]");
        assertThat(synthetic.getRepeatCount()).isEqualTo(1);
        assertThat(synthetic.getSourceAt()).isBetween(before, after);
    }

    @Test
    void syntheticEventInheritsTopologyFieldsFromTheTrigger() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 5L);
        Event triggerEvent = trigger("critical", CI_ID);
        triggerEvent.setNodeFqdn("node-1.example");
        triggerEvent.setSystemName("Billing");
        triggerEvent.setSubsystemName("Payments");

        applyThreshold(repository, triggerEvent, new ThresholdPolicy(5, 10));

        EventJpaEntity synthetic = savedEntity(repository);
        assertThat(synthetic.getSourceId()).isEqualTo(SOURCE_ID);
        assertThat(synthetic.getCiId()).isEqualTo(CI_ID);
        assertThat(synthetic.getNodeFqdn()).isEqualTo("node-1.example");
        assertThat(synthetic.getSystemName()).isEqualTo("Billing");
        assertThat(synthetic.getSubsystemName()).isEqualTo("Payments");
        assertThat(synthetic.getRawEventId()).isNull();
        assertThat(synthetic.getRootEventId()).isNull();
    }

    @Test
    void syntheticTitleAndDescriptionFollowTheConfiguredCountAndWindow() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 3L);

        Instant before = Instant.now();
        applyThreshold(repository, trigger("critical", CI_ID), new ThresholdPolicy(3, 7));
        Instant after = Instant.now();

        EventJpaEntity synthetic = savedEntity(repository);
        assertThat(synthetic.getTitle()).isEqualTo("Threshold: 3+ critical events in 7 minutes");
        assertThat(synthetic.getDescription())
                .isEqualTo("Auto-generated by threshold rule after 3 critical events within 7 minutes");
        assertThat((Instant) repository.callTo(COUNT_WITH_CI).args().get(3))
                .isBetween(minutesBefore(before, 7), minutesBefore(after, 7));
    }

    /** What the old boolean overload meant: count = 5 over a 10 minute window. */
    @Test
    void defaultPolicyMeansFiveCriticalEventsInTenMinutes() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(COUNT_WITH_CI, 5L);

        Instant before = Instant.now();
        applyThreshold(repository, trigger("critical", CI_ID), ThresholdPolicy.defaults());
        Instant after = Instant.now();

        assertThat(ThresholdPolicy.defaults()).isEqualTo(new ThresholdPolicy(5, 10));
        assertThat(savedEntity(repository).getTitle()).isEqualTo("Threshold: 5+ critical events in 10 minutes");
        assertThat((Instant) repository.callTo(COUNT_WITH_CI).args().get(3))
                .isBetween(minutesBefore(before, 10), minutesBefore(after, 10));
    }

    /** The threshold step of {@code ProcessRawEventBatchService}, window included. */
    private void applyThreshold(CharacterizationEventRepository repository, Event trigger, ThresholdPolicy policy) {
        EventPersistenceAdapter adapter = new EventPersistenceAdapter(repository.asRepository(), new EventJpaMapper());
        ThresholdEvaluator.Window window = new ThresholdEvaluator.Window() {

            @Override
            public long countRecentCritical(UUID sourceId, UUID ciId, Instant since) {
                return adapter.countRecentBySeverity(sourceId, ciId, "critical", since);
            }

            @Override
            public boolean hasRecentSynthetic(UUID sourceId, UUID ciId, String title, Instant since) {
                return adapter.existsRecentByTitle(sourceId, ciId, title, since);
            }
        };
        thresholdEvaluator.evaluate(trigger, policy, Instant.now(), window).ifPresent(adapter::save);
    }

    private static EventJpaEntity savedEntity(CharacterizationEventRepository repository) {
        return (EventJpaEntity) repository.callTo("save").args().getFirst();
    }

    private static Instant minutesBefore(Instant reference, int minutes) {
        return reference.minus(minutes, ChronoUnit.MINUTES);
    }

    private static Event trigger(String severity, UUID ciId) {
        Event event = new Event();
        event.setStatus("new");
        event.setSeverity(severity);
        event.setTitle("CPU load critical");
        event.setSourceId(SOURCE_ID);
        event.setCiId(ciId);
        event.setSourceAt(Instant.parse("2026-01-01T10:00:00Z"));
        return event;
    }
}

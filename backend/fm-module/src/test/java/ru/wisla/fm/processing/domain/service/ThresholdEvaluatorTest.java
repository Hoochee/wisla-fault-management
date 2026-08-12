package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.ThresholdPolicy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the assertions pinned against the current {@code ThresholdService} by
 * {@code ThresholdServiceCharacterizationTest} (task 1.5). The two counting queries stay behind
 * {@link ThresholdEvaluator.Window} so that the short-circuit ordering — the {@code exists} query
 * only runs once the count has breached, and reuses the same window start — is still decided by the
 * domain service rather than by its caller.
 */
class ThresholdEvaluatorTest {

    private static final UUID SOURCE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CI_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-02-03T08:15:00Z");

    private final ThresholdEvaluator evaluator = new ThresholdEvaluator();

    // --- entry conditions ----------------------------------------------------------------------

    /** Only the literal severity "critical" counts — "fatal" is more severe but does not qualify. */
    @ParameterizedTest
    @ValueSource(strings = {"fatal", "major", "minor", "warning", "normal"})
    void nonCriticalTriggerSeverityIsIgnored(String severity) {
        RecordingWindow window = new RecordingWindow();

        Optional<Event> synthetic = evaluator.evaluate(
                trigger(severity, CI_ID), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(synthetic).isEmpty();
        assertThat(window.calls).isEmpty();
    }

    // --- window keys and arithmetic -------------------------------------------------------------

    @Test
    void countQueryReceivesTheTriggerKeysAndTheWindowStart() {
        RecordingWindow window = new RecordingWindow().withCount(5L);

        evaluator.evaluate(trigger("critical", CI_ID), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(window.countSourceId).isEqualTo(SOURCE_ID);
        assertThat(window.countCiId).isEqualTo(CI_ID);
        assertThat(window.countSince).isEqualTo(NOW.minus(10, ChronoUnit.MINUTES));
    }

    @Test
    void aTriggerWithoutACiPassesANullCiThroughToTheWindow() {
        RecordingWindow window = new RecordingWindow().withCount(5L);

        evaluator.evaluate(trigger("critical", null), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(window.countSourceId).isEqualTo(SOURCE_ID);
        assertThat(window.countCiId).isNull();
    }

    @Test
    void countBelowTheThresholdStopsBeforeTheExistsQuery() {
        RecordingWindow window = new RecordingWindow().withCount(4L);

        Optional<Event> synthetic = evaluator.evaluate(
                trigger("critical", CI_ID), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(synthetic).isEmpty();
        assertThat(window.calls).containsExactly("count");
    }

    @Test
    void countEqualToTheThresholdAlreadyFires() {
        RecordingWindow window = new RecordingWindow().withCount(5L);

        Optional<Event> synthetic = evaluator.evaluate(
                trigger("critical", CI_ID), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(synthetic).isPresent();
        assertThat(window.calls).containsExactly("count", "exists");
    }

    @Test
    void existsQueryReusesTheSameWindowStartAsTheCountQuery() {
        RecordingWindow window = new RecordingWindow().withCount(5L);

        evaluator.evaluate(trigger("critical", CI_ID), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(window.existsSince).isEqualTo(window.countSince);
        assertThat(window.existsSourceId).isEqualTo(SOURCE_ID);
        assertThat(window.existsCiId).isEqualTo(CI_ID);
        assertThat(window.existsTitle).isEqualTo("Threshold: 5+ critical events in 10 minutes");
    }

    @Test
    void anExistingSyntheticWithTheSameTitleInTheWindowSuppressesANewOne() {
        RecordingWindow window = new RecordingWindow().withCount(99L).withExisting(true);

        Optional<Event> synthetic = evaluator.evaluate(
                trigger("critical", CI_ID), new ThresholdPolicy(5, 10), NOW, window);

        assertThat(synthetic).isEmpty();
        assertThat(window.calls).containsExactly("count", "exists");
    }

    // --- the synthetic event -------------------------------------------------------------------

    @Test
    void syntheticEventCarriesTheFrozenTitleSeverityAndAttributes() {
        RecordingWindow window = new RecordingWindow().withCount(5L);

        Event synthetic = evaluator
                .evaluate(trigger("critical", CI_ID), new ThresholdPolicy(5, 10), NOW, window)
                .orElseThrow();

        assertThat(synthetic.getStatus()).isEqualTo("new");
        assertThat(synthetic.getSeverity()).isEqualTo("fatal");
        assertThat(synthetic.getTitle()).isEqualTo("Threshold: 5+ critical events in 10 minutes");
        assertThat(synthetic.getDescription())
                .isEqualTo("Auto-generated by threshold rule after 5 critical events within 10 minutes");
        assertThat(synthetic.getAttributes()).isEqualTo("{\"synthetic\":true,\"ruleType\":\"threshold\"}");
        assertThat(synthetic.getTags()).isEqualTo("[]");
        assertThat(synthetic.getRepeatCount()).isEqualTo(1);
        assertThat(synthetic.getSourceAt()).isEqualTo(NOW);
    }

    @Test
    void syntheticEventInheritsTopologyFieldsFromTheTrigger() {
        RecordingWindow window = new RecordingWindow().withCount(5L);
        Event triggerEvent = trigger("critical", CI_ID);
        triggerEvent.setNodeFqdn("node-1.example");
        triggerEvent.setSystemName("Billing");
        triggerEvent.setSubsystemName("Payments");

        Event synthetic = evaluator
                .evaluate(triggerEvent, new ThresholdPolicy(5, 10), NOW, window)
                .orElseThrow();

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
        RecordingWindow window = new RecordingWindow().withCount(3L);

        Event synthetic = evaluator
                .evaluate(trigger("critical", CI_ID), new ThresholdPolicy(3, 7), NOW, window)
                .orElseThrow();

        assertThat(synthetic.getTitle()).isEqualTo("Threshold: 3+ critical events in 7 minutes");
        assertThat(synthetic.getDescription())
                .isEqualTo("Auto-generated by threshold rule after 3 critical events within 7 minutes");
        assertThat(window.countSince).isEqualTo(NOW.minus(7, ChronoUnit.MINUTES));
    }

    /** The default policy is the shorthand the boolean overload used: 5 criticals in 10 minutes. */
    @Test
    void defaultPolicyIsFiveCriticalEventsInTenMinutes() {
        assertThat(ThresholdPolicy.defaults()).isEqualTo(new ThresholdPolicy(5, 10));
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

    private static final class RecordingWindow implements ThresholdEvaluator.Window {

        private final List<String> calls = new ArrayList<>();
        private long count;
        private boolean existing;

        private UUID countSourceId;
        private UUID countCiId;
        private Instant countSince;
        private UUID existsSourceId;
        private UUID existsCiId;
        private String existsTitle;
        private Instant existsSince;

        RecordingWindow withCount(long value) {
            this.count = value;
            return this;
        }

        RecordingWindow withExisting(boolean value) {
            this.existing = value;
            return this;
        }

        @Override
        public long countRecentCritical(UUID sourceId, UUID ciId, Instant since) {
            calls.add("count");
            countSourceId = sourceId;
            countCiId = ciId;
            countSince = since;
            return count;
        }

        @Override
        public boolean hasRecentSynthetic(UUID sourceId, UUID ciId, String title, Instant since) {
            calls.add("exists");
            existsSourceId = sourceId;
            existsCiId = ciId;
            existsTitle = title;
            existsSince = since;
            return existing;
        }
    }
}

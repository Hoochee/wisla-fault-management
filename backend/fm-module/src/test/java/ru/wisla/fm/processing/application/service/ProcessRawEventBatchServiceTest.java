package ru.wisla.fm.processing.application.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.CiSnapshot;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.RuleGraph;
import ru.wisla.fm.processing.domain.service.CorrelationEvaluator;
import ru.wisla.fm.processing.domain.service.DedupMerger;
import ru.wisla.fm.processing.domain.service.EventFactory;
import ru.wisla.fm.processing.domain.service.PushMessageRenderer;
import ru.wisla.fm.processing.domain.service.RuleCanvasCompiler;
import ru.wisla.fm.processing.domain.service.RuleConditionEvaluator;
import ru.wisla.fm.processing.domain.service.RuleGraphTraverser;
import ru.wisla.fm.processing.domain.service.SwitchBranchSelector;
import ru.wisla.fm.processing.domain.service.ThresholdEvaluator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Spring-free test of the processing use case against fakes of all six outbound ports. It pins the
 * step order of {@code EventProcessingService.processBatch} / {@code processRawEvent}, which
 * {@code ProcessRawEventBatchService} reproduces.
 */
class ProcessRawEventBatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final UUID SOURCE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID CI_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String FQDN = "node-7.wisla.local";

    private final InMemoryProcessingPorts.RawEventState rawEventState = new InMemoryProcessingPorts.RawEventState();
    private final InMemoryProcessingPorts.EventStore eventStore = new InMemoryProcessingPorts.EventStore();
    private final InMemoryProcessingPorts.CiLookup ciLookup = new InMemoryProcessingPorts.CiLookup();
    private final InMemoryProcessingPorts.RuleDefinitions ruleDefinitions = new InMemoryProcessingPorts.RuleDefinitions();
    private final InMemoryProcessingPorts.Notifications notifications = new InMemoryProcessingPorts.Notifications();
    private final InMemoryProcessingPorts.PushNotifications pushNotifications =
            new InMemoryProcessingPorts.PushNotifications();

    // --- event creation ------------------------------------------------------------------------

    @Test
    void createsAnEventFromTheRawEventAndBindsTheConfigurationItem() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        ciLookup.with(FQDN, new CiSnapshot(CI_ID, FQDN, "Billing", "Payments"));

        service().process(List.of(raw.id()));

        assertThat(ciLookup.lookups()).containsExactly(FQDN);
        Event saved = eventStore.saved().getFirst();
        assertThat(saved.getStatus()).isEqualTo("new");
        assertThat(saved.getSeverity()).isEqualTo("critical");
        assertThat(saved.getTitle()).isEqualTo("Disk usage above 90%");
        assertThat(saved.getDescription()).isEqualTo("raw description");
        assertThat(saved.getSourceId()).isEqualTo(SOURCE_ID);
        assertThat(saved.getNodeFqdn()).isEqualTo(FQDN);
        assertThat(saved.getRawEventId()).isEqualTo(raw.id());
        assertThat(saved.getSourceAt()).isEqualTo(Instant.parse("2026-01-31T23:00:00Z"));
        assertThat(saved.getAttributes()).isEqualTo("{\"host\":\"node-7\"}");
        assertThat(saved.getCiId()).isEqualTo(CI_ID);
        assertThat(saved.getSystemName()).isEqualTo("Billing");
        assertThat(saved.getSubsystemName()).isEqualTo("Payments");
    }

    @Test
    void anUnresolvedConfigurationItemLeavesTheTopologyFieldsUnset() {
        IncomingRawEvent raw = raw("major", FQDN);
        rawEventState.with(raw);

        service().process(List.of(raw.id()));

        Event saved = eventStore.saved().getFirst();
        assertThat(saved.getCiId()).isNull();
        assertThat(saved.getSystemName()).isNull();
        assertThat(saved.getSubsystemName()).isNull();
        assertThat(rawEventState.processedMarks())
                .containsExactly(new InMemoryProcessingPorts.RawEventState.ProcessedMark(
                        raw.id(), saved.getId(), null));
    }

    @Test
    void marksTheRawEventProcessedWithTheSavedEventAndTheResolvedCi() {
        IncomingRawEvent raw = raw("major", FQDN);
        rawEventState.with(raw);
        ciLookup.with(FQDN, new CiSnapshot(CI_ID, FQDN, "Billing", null));

        service().process(List.of(raw.id()));

        Event saved = eventStore.saved().getFirst();
        assertThat(rawEventState.processedMarks())
                .containsExactly(new InMemoryProcessingPorts.RawEventState.ProcessedMark(
                        raw.id(), saved.getId(), CI_ID));
        assertThat(rawEventState.errorMarks()).isEmpty();
    }

    // --- batch entry conditions ----------------------------------------------------------------

    @Test
    void anEmptyOrNullBatchDoesNotEvenLoadTheRules() {
        service().process(List.of());
        service().process(null);

        assertThat(ruleDefinitions.lookups()).isZero();
        assertThat(eventStore.saved()).isEmpty();
    }

    @Test
    void anAlreadyProcessedRawEventIsSkippedWithoutAnyWrite() {
        IncomingRawEvent raw = processedRaw();
        rawEventState.with(raw);

        service().process(List.of(raw.id()));

        assertThat(ruleDefinitions.lookups()).isEqualTo(1);
        assertThat(ciLookup.lookups()).isEmpty();
        assertThat(eventStore.saved()).isEmpty();
        assertThat(rawEventState.processedMarks()).isEmpty();
        assertThat(rawEventState.errorMarks()).isEmpty();
    }

    @Test
    void anUnknownRawEventIdIsSkippedSilently() {
        service().process(List.of(UUID.randomUUID()));

        assertThat(eventStore.saved()).isEmpty();
        assertThat(rawEventState.errorMarks()).isEmpty();
    }

    // --- dedup branch -------------------------------------------------------------------------

    @Test
    void aDecisionWithoutDedupSavesTheEventDirectlyWithoutALookup() {
        IncomingRawEvent raw = raw("major", FQDN);
        rawEventState.with(raw);

        service().process(List.of(raw.id()));

        assertThat(eventStore.dedupKeys()).isEmpty();
        assertThat(eventStore.saved()).hasSize(1);
    }

    @Test
    void aDedupDecisionMergesIntoTheActiveDuplicate() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        Event existing = existingEvent("major");
        eventStore.withDuplicate(existing);
        ruleDefinitions.with(legacyPlan("dedup"));

        service().process(List.of(raw.id()));

        DedupKey key = eventStore.dedupKeys().getFirst();
        assertThat(key.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(key.title()).isEqualTo("Disk usage above 90%");
        assertThat(key.lookupRequired()).isTrue();

        assertThat(eventStore.saved()).containsExactly(existing);
        assertThat(existing.getRepeatCount()).isEqualTo(2);
        assertThat(existing.getLastRepeatAt()).isEqualTo(NOW);
        assertThat(existing.getSeverity()).isEqualTo("critical");
        assertThat(rawEventState.processedMarks().getFirst().eventId()).isEqualTo(existing.getId());
    }

    @Test
    void aDedupDecisionWithoutAnActiveDuplicateSavesTheCandidate() {
        IncomingRawEvent raw = raw("major", FQDN);
        rawEventState.with(raw);
        ruleDefinitions.with(legacyPlan("dedup"));

        service().process(List.of(raw.id()));

        assertThat(eventStore.dedupKeys()).hasSize(1);
        assertThat(eventStore.saved()).hasSize(1);
        assertThat(eventStore.saved().getFirst().getRepeatCount()).isEqualTo(1);
    }

    // --- threshold and correlation intents -----------------------------------------------------

    /**
     * What {@code ThresholdService.evaluateAfterProcessing(event, false)} used to express: with no
     * threshold intent the counting queries are never issued, even for a critical event.
     */
    @Test
    void aDecisionWithoutAThresholdIntentIssuesNoCountingQuery() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        ruleDefinitions.with(legacyPlan("dedup"));

        service().process(List.of(raw.id()));

        assertThat(eventStore.countQueries()).isEmpty();
        assertThat(eventStore.existsQueries()).isEmpty();
    }

    @Test
    void aThresholdIntentSavesTheSyntheticRollUpEvent() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        ciLookup.with(FQDN, new CiSnapshot(CI_ID, FQDN, "Billing", "Payments"));
        eventStore.withRecentCount(5);
        ruleDefinitions.with(legacyPlan("threshold"));

        service().process(List.of(raw.id()));

        assertThat(eventStore.countQueries()).containsExactly(
                new InMemoryProcessingPorts.EventStore.CountQuery(
                        SOURCE_ID, CI_ID, "critical", NOW.minus(10, ChronoUnit.MINUTES)));
        assertThat(eventStore.existsQueries()).containsExactly(
                new InMemoryProcessingPorts.EventStore.ExistsQuery(
                        SOURCE_ID,
                        CI_ID,
                        "Threshold: 5+ critical events in 10 minutes",
                        NOW.minus(10, ChronoUnit.MINUTES)));

        assertThat(eventStore.saved()).hasSize(2);
        Event synthetic = eventStore.saved().get(1);
        assertThat(synthetic.getSeverity()).isEqualTo("fatal");
        assertThat(synthetic.getTitle()).isEqualTo("Threshold: 5+ critical events in 10 minutes");
        assertThat(synthetic.getAttributes()).isEqualTo("{\"synthetic\":true,\"ruleType\":\"threshold\"}");
    }

    @Test
    void aThresholdIntentBelowTheCountSavesNothingExtra() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        eventStore.withRecentCount(4);
        ruleDefinitions.with(legacyPlan("threshold"));

        service().process(List.of(raw.id()));

        assertThat(eventStore.existsQueries()).isEmpty();
        assertThat(eventStore.saved()).hasSize(1);
    }

    @Test
    void anExistingSyntheticInTheWindowSuppressesANewOne() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        eventStore.withRecentCount(99).withSyntheticExisting();
        ruleDefinitions.with(legacyPlan("threshold"));

        service().process(List.of(raw.id()));

        assertThat(eventStore.existsQueries()).hasSize(1);
        assertThat(eventStore.saved()).hasSize(1);
    }

    @Test
    void aCorrelationIntentSetsTheRootEventIdAndSavesAgain() {
        IncomingRawEvent raw = raw("major", FQDN);
        rawEventState.with(raw);
        Event root = existingEvent("major");
        eventStore.withWindow(List.of(root, existingEvent("major")));
        ruleDefinitions.with(legacyPlan("correlation"));

        service().process(List.of(raw.id()));

        assertThat(eventStore.windowQueries()).hasSize(1);
        assertThat(eventStore.windowQueries().getFirst().matchField()).isEqualTo("title");
        assertThat(eventStore.windowQueries().getFirst().since())
                .isEqualTo(NOW.minus(10, ChronoUnit.MINUTES));

        Event saved = eventStore.saved().getFirst();
        assertThat(saved.getRootEventId()).isEqualTo(root.getId());
        assertThat(eventStore.saved()).containsExactly(saved, saved);
    }

    // --- notify and push intents ---------------------------------------------------------------

    @Test
    void notifyAndPushIntentsReachTheirPortsWithTheRenderedMessage() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        UUID ruleId = UUID.randomUUID();
        ruleDefinitions.with(canvasPlan(ruleId));

        service().process(List.of(raw.id()));

        UUID eventId = eventStore.saved().getFirst().getId();
        assertThat(notifications.deliveries()).containsExactly(
                new InMemoryProcessingPorts.Notifications.Delivery(ruleId, "email", "ops@wisla.local"));
        assertThat(pushNotifications.pushes()).containsExactly(
                new InMemoryProcessingPorts.PushNotifications.Push(
                        ruleId, eventId, "Disk usage above 90%", "Critical: Disk usage above 90%"));
    }

    @Test
    void repeatWhileSilencedSuppressesNotifyAndPush() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        Event existing = existingEvent("major");
        existing.silenceUntil(NOW.plus(30, ChronoUnit.MINUTES), UUID.randomUUID());
        eventStore.withDuplicate(existing);
        UUID ruleId = UUID.randomUUID();
        ruleDefinitions.with(canvasPlan(ruleId), legacyPlan("dedup"));

        service().process(List.of(raw.id()));

        assertThat(existing.getRepeatCount()).isEqualTo(2);
        assertThat(existing.getLastRepeatAt()).isEqualTo(NOW);
        assertThat(notifications.deliveries()).isEmpty();
        assertThat(pushNotifications.pushes()).isEmpty();
        assertThat(ruleDefinitions.runMarks()).hasSize(1);
        assertThat(ruleDefinitions.runMarks().getFirst().ruleIds()).contains(ruleId);
    }

    @Test
    void repeatAfterSilenceExpiresNotifiesAgain() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        Event existing = existingEvent("major");
        existing.silenceUntil(NOW.minus(1, ChronoUnit.MINUTES), UUID.randomUUID());
        eventStore.withDuplicate(existing);
        UUID ruleId = UUID.randomUUID();
        ruleDefinitions.with(canvasPlan(ruleId), legacyPlan("dedup"));

        service().process(List.of(raw.id()));

        assertThat(existing.getRepeatCount()).isEqualTo(2);
        assertThat(existing.getLastRepeatAt()).isEqualTo(NOW);
        assertThat(notifications.deliveries()).containsExactly(
                new InMemoryProcessingPorts.Notifications.Delivery(ruleId, "email", "ops@wisla.local"));
        assertThat(pushNotifications.pushes()).containsExactly(
                new InMemoryProcessingPorts.PushNotifications.Push(
                        ruleId, existing.getId(), "Disk usage above 90%", "Critical: Disk usage above 90%"));
        assertThat(ruleDefinitions.runMarks()).hasSize(1);
        assertThat(ruleDefinitions.runMarks().getFirst().ruleIds()).contains(ruleId);
    }

    // --- executed rules -----------------------------------------------------------------------

    @Test
    void everyRuleThatContributedAnIntentIsMarkedAsRun() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        UUID canvasRuleId = UUID.randomUUID();
        UUID thresholdRuleId = UUID.randomUUID();
        UUID correlationRuleId = UUID.randomUUID();
        eventStore.withRecentCount(0);
        ruleDefinitions.with(
                canvasPlan(canvasRuleId),
                legacyPlan("threshold", thresholdRuleId),
                legacyPlan("correlation", correlationRuleId));

        service().process(List.of(raw.id()));

        assertThat(ruleDefinitions.runMarks()).hasSize(1);
        InMemoryProcessingPorts.RuleDefinitions.RunMark mark = ruleDefinitions.runMarks().getFirst();
        assertThat(mark.ruleIds()).containsExactlyInAnyOrder(canvasRuleId, thresholdRuleId, correlationRuleId);
        assertThat(mark.now()).isEqualTo(NOW);
    }

    // --- error isolation ----------------------------------------------------------------------

    @Test
    void oneFailingRawEventIsRecordedWithoutAbortingTheBatch() {
        IncomingRawEvent failing = raw("major", "broken.wisla.local");
        IncomingRawEvent healthy = raw("major", FQDN);
        rawEventState.with(failing).with(healthy);
        ciLookup.failingOn("broken.wisla.local", "ci lookup exploded");

        assertThatCode(() -> service().process(List.of(failing.id(), healthy.id())))
                .doesNotThrowAnyException();

        assertThat(rawEventState.errorMarks()).containsExactly(
                new InMemoryProcessingPorts.RawEventState.ErrorMark(
                        failing.id(), null, "ci lookup exploded"));
        assertThat(eventStore.saved()).hasSize(1);
        assertThat(rawEventState.processedMarks()).hasSize(1);
        assertThat(rawEventState.processedMarks().getFirst().rawEventId()).isEqualTo(healthy.id());
    }

    /**
     * The CI is bound onto the raw-event row right after the lookup, so a failure later in the same
     * raw event still carries it into {@code recordError} — that is what the current managed-entity
     * mutation does before the {@code catch} block saves it.
     */
    @Test
    void aFailureAfterTheCiLookupStillCarriesTheResolvedCiId() {
        IncomingRawEvent raw = raw("critical", FQDN);
        rawEventState.with(raw);
        ciLookup.with(FQDN, new CiSnapshot(CI_ID, FQDN, "Billing", "Payments"));
        eventStore.failingOnSave("events table exploded");

        service().process(List.of(raw.id()));

        assertThat(rawEventState.errorMarks()).containsExactly(
                new InMemoryProcessingPorts.RawEventState.ErrorMark(
                        raw.id(), CI_ID, "events table exploded"));
        assertThat(rawEventState.processedMarks()).isEmpty();
    }

    // --- fixtures -----------------------------------------------------------------------------

    private ProcessRawEventBatchService service() {
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        return new ProcessRawEventBatchService(
                rawEventState,
                eventStore,
                ciLookup,
                ruleDefinitions,
                notifications,
                pushNotifications,
                new RuleGraphTraverser(conditionEvaluator, new SwitchBranchSelector(conditionEvaluator)),
                new EventFactory(),
                new DedupMerger(),
                new ThresholdEvaluator(),
                new CorrelationEvaluator(),
                new PushMessageRenderer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CompiledRulePlan legacyPlan(String ruleType) {
        return legacyPlan(ruleType, UUID.randomUUID());
    }

    private static CompiledRulePlan legacyPlan(String ruleType, UUID ruleId) {
        return new RuleCanvasCompiler().compile(ruleId, ruleType, RuleGraph.empty());
    }

    private static CompiledRulePlan canvasPlan(UUID ruleId) {
        RuleGraph graph = new RuleGraph(
                List.of(
                        Map.of("id", "n1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "n2", "type", "notify",
                                "config", Map.of("channel", "email", "emailAddress", "ops@wisla.local")),
                        Map.of("id", "n3", "type", "push", "config", Map.of("message", "Critical: {title}"))),
                List.of(
                        Map.of("id", "e1", "source", "n1", "target", "n2"),
                        Map.of("id", "e2", "source", "n2", "target", "n3")));
        return new RuleCanvasCompiler().compile(ruleId, "notify", graph);
    }

    private static IncomingRawEvent raw(String severity, String nodeFqdn) {
        return new IncomingRawEvent(
                UUID.randomUUID(),
                SOURCE_ID,
                "ext-1",
                "Disk usage above 90%",
                "raw description",
                severity,
                "new",
                nodeFqdn,
                null,
                "{\"host\":\"node-7\"}",
                Instant.parse("2026-01-31T23:00:00Z"),
                false);
    }

    private static IncomingRawEvent processedRaw() {
        return new IncomingRawEvent(
                UUID.randomUUID(),
                SOURCE_ID,
                "ext-done",
                "Already processed",
                null,
                "major",
                "new",
                FQDN,
                null,
                "{}",
                Instant.parse("2026-01-31T23:00:00Z"),
                true);
    }

    private static Event existingEvent(String severity) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setSeverity(severity);
        event.setTitle("Disk usage above 90%");
        event.setSourceId(SOURCE_ID);
        event.setSourceAt(Instant.parse("2026-01-31T22:00:00Z"));
        return event;
    }
}

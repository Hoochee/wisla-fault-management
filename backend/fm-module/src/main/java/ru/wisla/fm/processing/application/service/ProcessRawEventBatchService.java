package ru.wisla.fm.processing.application.service;

import ru.wisla.fm.processing.application.port.in.ProcessRawEventBatchUseCase;
import ru.wisla.fm.processing.application.port.out.CiLookupPort;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.application.port.out.NotificationPort;
import ru.wisla.fm.processing.application.port.out.PushNotificationPort;
import ru.wisla.fm.processing.application.port.out.RawEventStatePort;
import ru.wisla.fm.processing.application.port.out.RuleDefinitionPort;
import ru.wisla.fm.processing.domain.CiSnapshot;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.DedupPolicy;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.ProcessingDecision;
import ru.wisla.fm.processing.domain.ThresholdPolicy;
import ru.wisla.fm.processing.domain.service.CorrelationEvaluator;
import ru.wisla.fm.processing.domain.service.DedupMerger;
import ru.wisla.fm.processing.domain.service.EventFactory;
import ru.wisla.fm.processing.domain.service.PushMessageRenderer;
import ru.wisla.fm.processing.domain.service.RuleGraphTraverser;
import ru.wisla.fm.processing.domain.service.ThresholdEvaluator;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns ingested raw events into events, applying the enabled rules. Reproduces
 * {@code EventProcessingService.processBatch} / {@code processRawEvent} step for step, including the
 * {@code try/catch} that records {@code raw_events.processing_error} for a single raw event without
 * aborting the batch or the surrounding ingest transaction (design decision D3).
 */
public class ProcessRawEventBatchService implements ProcessRawEventBatchUseCase {

    private final RawEventStatePort rawEventState;
    private final EventStorePort eventStore;
    private final CiLookupPort ciLookup;
    private final RuleDefinitionPort ruleDefinitions;
    private final NotificationPort notifications;
    private final PushNotificationPort pushNotifications;
    private final RuleGraphTraverser ruleGraphTraverser;
    private final EventFactory eventFactory;
    private final DedupMerger dedupMerger;
    private final ThresholdEvaluator thresholdEvaluator;
    private final CorrelationEvaluator correlationEvaluator;
    private final PushMessageRenderer pushMessageRenderer;
    private final Clock clock;
    private final ThresholdEvaluator.Window thresholdWindow;
    private final CorrelationEvaluator.Window correlationWindow;

    public ProcessRawEventBatchService(RawEventStatePort rawEventState,
                                      EventStorePort eventStore,
                                      CiLookupPort ciLookup,
                                      RuleDefinitionPort ruleDefinitions,
                                      NotificationPort notifications,
                                      PushNotificationPort pushNotifications,
                                      RuleGraphTraverser ruleGraphTraverser,
                                      EventFactory eventFactory,
                                      DedupMerger dedupMerger,
                                      ThresholdEvaluator thresholdEvaluator,
                                      CorrelationEvaluator correlationEvaluator,
                                      PushMessageRenderer pushMessageRenderer,
                                      Clock clock) {
        this.rawEventState = rawEventState;
        this.eventStore = eventStore;
        this.ciLookup = ciLookup;
        this.ruleDefinitions = ruleDefinitions;
        this.notifications = notifications;
        this.pushNotifications = pushNotifications;
        this.ruleGraphTraverser = ruleGraphTraverser;
        this.eventFactory = eventFactory;
        this.dedupMerger = dedupMerger;
        this.thresholdEvaluator = thresholdEvaluator;
        this.correlationEvaluator = correlationEvaluator;
        this.pushMessageRenderer = pushMessageRenderer;
        this.clock = clock;
        this.thresholdWindow = new ThresholdWindow();
        this.correlationWindow = new CorrelationWindow();
    }

    @Override
    public void process(List<UUID> rawEventIds) {
        if (rawEventIds == null || rawEventIds.isEmpty()) {
            return;
        }
        List<CompiledRulePlan> compiledRules = ruleDefinitions.findEnabledRules();
        for (UUID rawEventId : rawEventIds) {
            rawEventState.findById(rawEventId)
                    .ifPresent(raw -> processRawEvent(raw, compiledRules));
        }
    }

    private void processRawEvent(IncomingRawEvent raw, List<CompiledRulePlan> compiledRules) {
        if (raw.processed()) {
            return;
        }
        UUID ciId = null;
        try {
            CiSnapshot ci = ciLookup.findOrCreateByFqdn(raw.nodeFqdn()).orElse(null);
            if (ci != null) {
                ciId = ci.id();
            }

            Event event = eventFactory.create(raw, ci);

            ProcessingDecision decision = ruleGraphTraverser.resolve(raw, event, compiledRules);

            Event saved = decision.dedupEnabled()
                    ? mergeOrCreate(event, decision.dedupPolicy())
                    : eventStore.save(event);

            Set<UUID> executedRules = new HashSet<>(decision.executedRuleIds());
            for (ProcessingDecision.ThresholdIntent intent : decision.thresholdIntents()) {
                applyThreshold(saved, new ThresholdPolicy(intent.count(), intent.windowMin()));
                executedRules.add(intent.ruleId());
            }
            for (ProcessingDecision.CorrelationIntent intent : decision.correlationIntents()) {
                applyCorrelation(saved, intent.policy());
                executedRules.add(intent.ruleId());
            }
            for (ProcessingDecision.NotifyIntent intent : decision.notifyIntents()) {
                notifications.notify(intent.ruleId(), intent.channel(), intent.emailAddress());
                executedRules.add(intent.ruleId());
            }
            for (ProcessingDecision.PushIntent intent : decision.pushIntents()) {
                String message = pushMessageRenderer.render(intent.message(), saved);
                pushNotifications.createPush(intent.ruleId(), saved.getId(), saved.getTitle(), message);
                executedRules.add(intent.ruleId());
            }

            ruleDefinitions.markRun(executedRules, clock.instant());

            rawEventState.markProcessed(raw.id(), saved.getId(), ciId);
        } catch (Exception ex) {
            rawEventState.recordError(raw.id(), ciId, ex.getMessage());
        }
    }

    private Event mergeOrCreate(Event candidate, DedupPolicy policy) {
        Optional<Event> existing = eventStore.findActiveDuplicate(DedupKey.from(candidate, policy));
        if (existing.isPresent()) {
            return eventStore.save(dedupMerger.merge(existing.get(), candidate, clock.instant()));
        }
        return eventStore.save(candidate);
    }

    private void applyThreshold(Event processedEvent, ThresholdPolicy policy) {
        thresholdEvaluator.evaluate(processedEvent, policy, clock.instant(), thresholdWindow)
                .ifPresent(eventStore::save);
    }

    private void applyCorrelation(Event processedEvent, CorrelationPolicy policy) {
        if (correlationEvaluator.evaluate(processedEvent, policy, clock.instant(), correlationWindow)) {
            eventStore.save(processedEvent);
        }
    }

    /**
     * The two threshold queries as the evaluator sees them. Only the literal severity
     * {@code "critical"} is ever counted, exactly as {@code ThresholdService.countRecentCritical} did.
     */
    private final class ThresholdWindow implements ThresholdEvaluator.Window {

        @Override
        public long countRecentCritical(UUID sourceId, UUID ciId, Instant since) {
            return eventStore.countRecentBySeverity(sourceId, ciId, "critical", since);
        }

        @Override
        public boolean hasRecentSynthetic(UUID sourceId, UUID ciId, String title, Instant since) {
            return eventStore.existsRecentByTitle(sourceId, ciId, title, since);
        }
    }

    private final class CorrelationWindow implements CorrelationEvaluator.Window {

        @Override
        public List<Event> findWindow(Event processedEvent, String matchField, Instant since) {
            return eventStore.findWindow(processedEvent, matchField, since);
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return eventStore.findById(id);
        }
    }
}

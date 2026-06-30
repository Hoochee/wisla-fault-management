package ru.wisla.fm.processing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.cmdb.domain.ConfigurationItemEntity;
import ru.wisla.fm.cmdb.service.CiService;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.ingestion.persistence.RawEventRepository;
import ru.wisla.fm.processing.canvas.CompiledRulePlan;
import ru.wisla.fm.processing.canvas.ProcessingDecision;
import ru.wisla.fm.processing.canvas.RuleCanvasEngine;
import ru.wisla.fm.notifications.api.NotifyStubService;
import ru.wisla.fm.notifications.api.PushNotificationService;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventRepository;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EventProcessingService {

    private final RawEventRepository rawEventRepository;
    private final CiService ciService;
    private final DedupService dedupService;
    private final EventRepository eventRepository;
    private final ProcessingRuleRepository processingRuleRepository;
    private final ThresholdService thresholdService;
    private final CorrelationService correlationService;
    private final RuleCanvasEngine ruleCanvasEngine;
    private final PushNotificationService pushNotificationService;
    private final NotifyStubService notifyStubService;

    public EventProcessingService(RawEventRepository rawEventRepository,
                                  CiService ciService,
                                  DedupService dedupService,
                                  EventRepository eventRepository,
                                  ProcessingRuleRepository processingRuleRepository,
                                  ThresholdService thresholdService,
                                  CorrelationService correlationService,
                                  RuleCanvasEngine ruleCanvasEngine,
                                  PushNotificationService pushNotificationService,
                                  NotifyStubService notifyStubService) {
        this.rawEventRepository = rawEventRepository;
        this.ciService = ciService;
        this.dedupService = dedupService;
        this.eventRepository = eventRepository;
        this.processingRuleRepository = processingRuleRepository;
        this.thresholdService = thresholdService;
        this.correlationService = correlationService;
        this.ruleCanvasEngine = ruleCanvasEngine;
        this.pushNotificationService = pushNotificationService;
        this.notifyStubService = notifyStubService;
    }

    @Transactional
    public void processBatch(List<UUID> rawEventIds) {
        if (rawEventIds == null || rawEventIds.isEmpty()) {
            return;
        }
        List<ProcessingRuleEntity> enabledRules =
                processingRuleRepository.findByEnabledTrueOrderByCreatedAtAsc();
        Map<UUID, CompiledRulePlan> compiledRules = ruleCanvasEngine.compileRules(enabledRules);
        for (UUID rawEventId : rawEventIds) {
            rawEventRepository.findById(rawEventId)
                    .ifPresent(raw -> processRawEvent(raw, enabledRules, compiledRules));
        }
    }

    private void processRawEvent(
            RawEventEntity raw,
            List<ProcessingRuleEntity> enabledRules,
            Map<UUID, CompiledRulePlan> compiledRules
    ) {
        if (raw.isProcessed()) {
            return;
        }
        try {
            ConfigurationItemEntity ci = ciService.findOrCreateByFqdn(raw.getNodeFqdn()).orElse(null);
            if (ci != null) {
                raw.setCiId(ci.getId());
            }

            EventEntity event = new EventEntity();
            event.setStatus("new");
            event.setSeverity(raw.getSeverity());
            event.setTitle(raw.getTitle());
            event.setDescription(raw.getDescription());
            event.setSourceId(raw.getSourceId());
            event.setNodeFqdn(raw.getNodeFqdn());
            event.setRawEventId(raw.getId());
            event.setSourceAt(raw.getSourceAt());
            event.setAttributes(raw.getPayload());
            if (ci != null) {
                event.setCiId(ci.getId());
                event.setSystemName(ci.getSystemName());
                event.setSubsystemName(ci.getSubsystemName());
            }

            ProcessingDecision decision = ruleCanvasEngine.resolveActions(
                    raw, event, enabledRules.stream()
                            .map(rule -> compiledRules.get(rule.getId()))
                            .filter(java.util.Objects::nonNull)
                            .toList()
            );

            EventEntity saved = decision.dedupEnabled()
                    ? dedupService.mergeOrCreate(event, decision.dedupConfig())
                    : eventRepository.save(event);

            Set<UUID> executedRules = new HashSet<>(decision.executedRuleIds());
            for (ProcessingDecision.ThresholdIntent intent : decision.thresholdIntents()) {
                thresholdService.evaluateAfterProcessing(saved, intent.count(), intent.windowMin());
                executedRules.add(intent.ruleId());
            }
            for (ProcessingDecision.CorrelationIntent intent : decision.correlationIntents()) {
                correlationService.evaluateAfterProcessing(saved, intent.config());
                executedRules.add(intent.ruleId());
            }
            for (ProcessingDecision.NotifyIntent intent : decision.notifyIntents()) {
                notifyStubService.execute(intent);
                executedRules.add(intent.ruleId());
            }
            for (ProcessingDecision.PushIntent intent : decision.pushIntents()) {
                String message = resolvePushMessage(intent.message(), saved);
                pushNotificationService.create(intent.ruleId(), saved.getId(), saved.getTitle(), message);
                executedRules.add(intent.ruleId());
            }

            ruleCanvasEngine.updateLastRunAt(executedRules);

            raw.setProcessed(true);
            raw.setProcessedEventId(saved.getId());
            rawEventRepository.save(raw);
        } catch (Exception ex) {
            raw.setProcessingError(ex.getMessage());
            rawEventRepository.save(raw);
        }
    }

    private static String resolvePushMessage(String template, EventEntity event) {
        if (template != null && !template.isBlank()) {
            return template
                    .replace("{title}", event.getTitle() != null ? event.getTitle() : "")
                    .replace("{severity}", event.getSeverity() != null ? event.getSeverity() : "");
        }
        return event.getTitle() != null ? event.getTitle() : "Событие";
    }
}

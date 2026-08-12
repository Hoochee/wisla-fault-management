package ru.wisla.fm.processing.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.wisla.fm.processing.application.port.in.ProcessRawEventBatchUseCase;
import ru.wisla.fm.processing.application.port.out.CiLookupPort;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.application.port.out.NotificationPort;
import ru.wisla.fm.processing.application.port.out.PushNotificationPort;
import ru.wisla.fm.processing.application.port.out.RawEventStatePort;
import ru.wisla.fm.processing.application.port.out.RuleDefinitionPort;
import ru.wisla.fm.processing.application.service.ProcessRawEventBatchService;
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

/**
 * Spring wiring for the processing context. The use case and the domain services carry no stereotype
 * annotation, so they are declared here instead. The processing path stays non-transactional of its
 * own accord: it runs inside the caller's transaction, which the ingest inbound adapter opens
 * (design decision D3).
 */
@Configuration
public class ProcessingConfig {

    @Bean
    RuleCanvasCompiler ruleCanvasCompiler() {
        return new RuleCanvasCompiler();
    }

    @Bean
    RuleConditionEvaluator ruleConditionEvaluator() {
        return new RuleConditionEvaluator();
    }

    @Bean
    SwitchBranchSelector switchBranchSelector(RuleConditionEvaluator ruleConditionEvaluator) {
        return new SwitchBranchSelector(ruleConditionEvaluator);
    }

    @Bean
    RuleGraphTraverser ruleGraphTraverser(RuleConditionEvaluator ruleConditionEvaluator,
                                          SwitchBranchSelector switchBranchSelector) {
        return new RuleGraphTraverser(ruleConditionEvaluator, switchBranchSelector);
    }

    @Bean
    EventFactory eventFactory() {
        return new EventFactory();
    }

    @Bean
    DedupMerger dedupMerger() {
        return new DedupMerger();
    }

    @Bean
    ThresholdEvaluator thresholdEvaluator() {
        return new ThresholdEvaluator();
    }

    @Bean
    CorrelationEvaluator correlationEvaluator() {
        return new CorrelationEvaluator();
    }

    @Bean
    PushMessageRenderer pushMessageRenderer() {
        return new PushMessageRenderer();
    }

    @Bean
    public ProcessRawEventBatchUseCase processRawEventBatchUseCase(RawEventStatePort rawEventState,
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
                                                                   PushMessageRenderer pushMessageRenderer) {
        return new ProcessRawEventBatchService(
                rawEventState,
                eventStore,
                ciLookup,
                ruleDefinitions,
                notifications,
                pushNotifications,
                ruleGraphTraverser,
                eventFactory,
                dedupMerger,
                thresholdEvaluator,
                correlationEvaluator,
                pushMessageRenderer,
                Clock.systemUTC());
    }
}

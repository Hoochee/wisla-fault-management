package ru.wisla.fm.processing.canvas;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.rules.api.RuleCanvasDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCanvasEngineTest {

    @Test
    void conditionFiltersNonMatchingEvent() {
        RuleCanvasCompiler compiler = new RuleCanvasCompiler();
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        SwitchBranchSelector switchBranchSelector = new SwitchBranchSelector(conditionEvaluator);
        RuleCanvasEngine engine = new RuleCanvasEngine(
                compiler, conditionEvaluator, switchBranchSelector, null, null
        );

        UUID ruleId = UUID.randomUUID();
        RuleCanvasDto canvas = new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b2", "type", "condition",
                                "config", Map.of("field", "severity", "operator", "eq", "value", "critical")),
                        Map.of("id", "b4", "type", "dedup", "config", Map.of("key", "source_id + title + ci_id"))
                ),
                List.of(
                        Map.of("id", "e1", "source", "b1", "target", "b2"),
                        Map.of("id", "e2", "source", "b2", "target", "b4")
                )
        );
        CompiledRulePlan plan = compiler.compile(ruleId, "dedup", canvas);

        RawEventEntity raw = new RawEventEntity();
        raw.setSeverity("warning");
        raw.setTitle("Disk");
        EventEntity event = new EventEntity();
        event.setSeverity("warning");
        event.setTitle("Disk");

        ProcessingDecision decision = engine.resolveActions(raw, event, List.of(plan));

        assertThat(decision.dedupEnabled()).isFalse();
    }

    @Test
    void legacyFallbackEnablesDedupForEmptyCanvas() {
        RuleCanvasCompiler compiler = new RuleCanvasCompiler();
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        SwitchBranchSelector switchBranchSelector = new SwitchBranchSelector(conditionEvaluator);
        RuleCanvasEngine engine = new RuleCanvasEngine(
                compiler, conditionEvaluator, switchBranchSelector, null, null
        );

        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = compiler.compile(ruleId, "dedup", new RuleCanvasDto(List.of(), List.of()));

        RawEventEntity raw = new RawEventEntity();
        raw.setSeverity("major");
        EventEntity event = new EventEntity();

        ProcessingDecision decision = engine.resolveActions(raw, event, List.of(plan));

        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.dedupRuleId()).isEqualTo(ruleId);
    }

    @Test
    void pushBlockProducesPushIntent() {
        RuleCanvasCompiler compiler = new RuleCanvasCompiler();
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        SwitchBranchSelector switchBranchSelector = new SwitchBranchSelector(conditionEvaluator);
        RuleCanvasEngine engine = new RuleCanvasEngine(
                compiler, conditionEvaluator, switchBranchSelector, null, null
        );

        UUID ruleId = UUID.randomUUID();
        RuleCanvasDto canvas = new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b8", "type", "push", "config", Map.of("message", "Critical: {title}"))),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b8"))
        );
        CompiledRulePlan plan = compiler.compile(ruleId, "threshold", canvas);

        RawEventEntity raw = new RawEventEntity();
        raw.setSeverity("critical");
        raw.setTitle("Disk full");
        EventEntity event = new EventEntity();
        event.setSeverity("critical");
        event.setTitle("Disk full");

        ProcessingDecision decision = engine.resolveActions(raw, event, List.of(plan));

        assertThat(decision.pushIntents()).hasSize(1);
        assertThat(decision.pushIntents().getFirst().ruleId()).isEqualTo(ruleId);
        assertThat(decision.pushIntents().getFirst().message()).isEqualTo("Critical: {title}");
        assertThat(decision.executedRuleIds()).contains(ruleId);
    }

    @Test
    void notifyBlockProducesNotifyIntent() {
        RuleCanvasCompiler compiler = new RuleCanvasCompiler();
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        SwitchBranchSelector switchBranchSelector = new SwitchBranchSelector(conditionEvaluator);
        RuleCanvasEngine engine = new RuleCanvasEngine(
                compiler, conditionEvaluator, switchBranchSelector, null, null
        );

        UUID ruleId = UUID.randomUUID();
        RuleCanvasDto canvas = new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b6", "type", "notify",
                                "config", Map.of("channel", "email", "emailAddress", "ops@wisla.local"))),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b6"))
        );
        CompiledRulePlan plan = compiler.compile(ruleId, "threshold", canvas);

        RawEventEntity raw = new RawEventEntity();
        EventEntity event = new EventEntity();

        ProcessingDecision decision = engine.resolveActions(raw, event, List.of(plan));

        assertThat(decision.notifyIntents()).hasSize(1);
        assertThat(decision.notifyIntents().getFirst().channel()).isEqualTo("email");
        assertThat(decision.notifyIntents().getFirst().emailAddress()).isEqualTo("ops@wisla.local");
        assertThat(decision.executedRuleIds()).contains(ruleId);
    }
}

package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.ProcessingDecision;
import ru.wisla.fm.processing.domain.RuleGraph;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four cases that used to drive {@code RuleCanvasEngine.resolveActions}, carried over unchanged
 * onto {@link RuleGraphTraverser}, which now owns that traversal. Kept as a separate class from
 * {@link RuleGraphTraverserTest} so the original assertions stay identifiable.
 */
class RuleCanvasEngineTest {

    private final RuleCanvasCompiler compiler = new RuleCanvasCompiler();
    private final RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
    private final RuleGraphTraverser traverser =
            new RuleGraphTraverser(conditionEvaluator, new SwitchBranchSelector(conditionEvaluator));

    @Test
    void conditionFiltersNonMatchingEvent() {
        UUID ruleId = UUID.randomUUID();
        RuleGraph canvas = new RuleGraph(
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

        ProcessingDecision decision = traverser.resolve(
                raw("warning", "Disk"), event("warning", "Disk"), List.of(plan));

        assertThat(decision.dedupEnabled()).isFalse();
    }

    @Test
    void legacyFallbackEnablesDedupForEmptyCanvas() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = compiler.compile(ruleId, "dedup", new RuleGraph(List.of(), List.of()));

        ProcessingDecision decision = traverser.resolve(
                raw("major", null), event(null, null), List.of(plan));

        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.dedupRuleId()).isEqualTo(ruleId);
    }

    @Test
    void pushBlockProducesPushIntent() {
        UUID ruleId = UUID.randomUUID();
        RuleGraph canvas = new RuleGraph(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b8", "type", "push", "config", Map.of("message", "Critical: {title}"))),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b8"))
        );
        CompiledRulePlan plan = compiler.compile(ruleId, "threshold", canvas);

        ProcessingDecision decision = traverser.resolve(
                raw("critical", "Disk full"), event("critical", "Disk full"), List.of(plan));

        assertThat(decision.pushIntents()).hasSize(1);
        assertThat(decision.pushIntents().getFirst().ruleId()).isEqualTo(ruleId);
        assertThat(decision.pushIntents().getFirst().message()).isEqualTo("Critical: {title}");
        assertThat(decision.executedRuleIds()).contains(ruleId);
    }

    @Test
    void notifyBlockProducesNotifyIntent() {
        UUID ruleId = UUID.randomUUID();
        RuleGraph canvas = new RuleGraph(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b6", "type", "notify",
                                "config", Map.of("channel", "email", "emailAddress", "ops@wisla.local"))),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b6"))
        );
        CompiledRulePlan plan = compiler.compile(ruleId, "threshold", canvas);

        ProcessingDecision decision = traverser.resolve(
                raw(null, null), event(null, null), List.of(plan));

        assertThat(decision.notifyIntents()).hasSize(1);
        assertThat(decision.notifyIntents().getFirst().channel()).isEqualTo("email");
        assertThat(decision.notifyIntents().getFirst().emailAddress()).isEqualTo("ops@wisla.local");
        assertThat(decision.executedRuleIds()).contains(ruleId);
    }

    private static IncomingRawEvent raw(String severity, String title) {
        return new IncomingRawEvent(
                UUID.randomUUID(), UUID.randomUUID(), "ext-1", title, null,
                severity, "new", null, null, "{}", Instant.parse("2026-01-01T10:00:00Z"), false);
    }

    private static Event event(String severity, String title) {
        Event event = new Event();
        event.setSeverity(severity);
        event.setTitle(title);
        return event;
    }
}

package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.DedupPolicy;
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
 * Pins the graph traversal lifted out of {@code RuleCanvasEngine.traverseRule} /
 * {@code resolveActions} / {@code applyLegacyFallback}.
 */
class RuleGraphTraverserTest {

    private static final Map<String, Object> STREAM_TRIGGER =
            Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream"));

    private final RuleCanvasCompiler compiler = new RuleCanvasCompiler();
    private final RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
    private final RuleGraphTraverser traverser =
            new RuleGraphTraverser(conditionEvaluator, new SwitchBranchSelector(conditionEvaluator));

    // --- action nodes ---------------------------------------------------------------------------

    @Test
    void aDedupNodeEnablesDedupWithTheConfiguredKey() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "dedup", graph(
                List.of(STREAM_TRIGGER, node("b4", "dedup", Map.of("key", "source_id + title"))),
                List.of(edge("e1", "b1", "b4"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.dedupRuleId()).isEqualTo(ruleId);
        assertThat(decision.dedupPolicy()).isEqualTo(new DedupPolicy(true, true, false));
        assertThat(decision.executedRuleIds()).containsExactly(ruleId);
    }

    @Test
    void aThresholdNodeProducesAThresholdIntent() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "threshold", graph(
                List.of(STREAM_TRIGGER, node("b5", "threshold", Map.of("count", "3", "windowMin", "7"))),
                List.of(edge("e1", "b1", "b5"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.thresholdIntents()).hasSize(1);
        assertThat(decision.thresholdIntents().getFirst().ruleId()).isEqualTo(ruleId);
        assertThat(decision.thresholdIntents().getFirst().count()).isEqualTo(3);
        assertThat(decision.thresholdIntents().getFirst().windowMin()).isEqualTo(7);
        assertThat(decision.executedRuleIds()).containsExactly(ruleId);
    }

    @Test
    void aCorrelationNodeProducesACorrelationIntent() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "correlation", graph(
                List.of(STREAM_TRIGGER,
                        node("b7", "correlation", Map.of("count", "4", "windowMin", "20", "matchField", "severity"))),
                List.of(edge("e1", "b1", "b7"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.correlationIntents()).hasSize(1);
        assertThat(decision.correlationIntents().getFirst().policy())
                .isEqualTo(new CorrelationPolicy(4, 20, "severity"));
        assertThat(decision.executedRuleIds()).containsExactly(ruleId);
    }

    @Test
    void aNotifyNodeProducesANotifyIntent() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "threshold", graph(
                List.of(STREAM_TRIGGER,
                        node("b6", "notify", Map.of("channel", "email", "emailAddress", "ops@wisla.local"))),
                List.of(edge("e1", "b1", "b6"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.notifyIntents()).hasSize(1);
        assertThat(decision.notifyIntents().getFirst().channel()).isEqualTo("email");
        assertThat(decision.notifyIntents().getFirst().emailAddress()).isEqualTo("ops@wisla.local");
        assertThat(decision.executedRuleIds()).containsExactly(ruleId);
    }

    @Test
    void aPushNodeProducesAPushIntentCarryingTheRawTemplate() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "threshold", graph(
                List.of(STREAM_TRIGGER, node("b8", "push", Map.of("message", "Critical: {title}"))),
                List.of(edge("e1", "b1", "b8"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.pushIntents()).hasSize(1);
        assertThat(decision.pushIntents().getFirst().message()).isEqualTo("Critical: {title}");
        assertThat(decision.executedRuleIds()).containsExactly(ruleId);
    }

    @Test
    void aTriggerNodeOnItsOwnProducesNothing() {
        CompiledRulePlan plan = plan(UUID.randomUUID(), "dedup",
                graph(List.of(STREAM_TRIGGER), List.of()));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.dedupEnabled()).isFalse();
        assertThat(decision.executedRuleIds()).isEmpty();
    }

    @Test
    void aCanvasWithoutAStreamTriggerIsSkipped() {
        CompiledRulePlan plan = plan(UUID.randomUUID(), "dedup", graph(
                List.of(node("b0", "trigger", Map.of("triggerType", "manual")), node("b4", "dedup", Map.of())),
                List.of(edge("e1", "b0", "b4"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(plan.triggerNodeId()).isNull();
        assertThat(decision.dedupEnabled()).isFalse();
    }

    @Test
    void aBranchFollowsThroughSeveralActionNodes() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "dedup", graph(
                List.of(STREAM_TRIGGER,
                        node("b4", "dedup", Map.of("key", "source_id + title + ci_id")),
                        node("b8", "push", Map.of("message", "hi"))),
                List.of(edge("e1", "b1", "b4"), edge("e2", "b4", "b8"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.pushIntents()).hasSize(1);
    }

    /** An unrecognised node type is a dead end: it neither acts nor forwards the traversal. */
    @Test
    void anUnknownNodeTypeStopsThatBranch() {
        CompiledRulePlan plan = plan(UUID.randomUUID(), "dedup", graph(
                List.of(STREAM_TRIGGER, node("bx", "enrich", Map.of()), node("b4", "dedup", Map.of())),
                List.of(edge("e1", "b1", "bx"), edge("e2", "bx", "b4"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.dedupEnabled()).isFalse();
    }

    // --- condition nodes ------------------------------------------------------------------------

    @Test
    void aMatchingConditionLetsTheTraversalContinue() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "dedup", conditionalDedupCanvas());

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.dedupRuleId()).isEqualTo(ruleId);
    }

    @Test
    void aFalseConditionStopsTheWholeTraversal() {
        CompiledRulePlan plan = plan(UUID.randomUUID(), "dedup", conditionalDedupCanvas());

        ProcessingDecision decision = resolve(plan, "warning");

        assertThat(decision.dedupEnabled()).isFalse();
        assertThat(decision.executedRuleIds()).isEmpty();
    }

    /**
     * A false condition returns from the traversal entirely rather than only pruning its own branch,
     * so a sibling action queued alongside it is dropped too.
     */
    @Test
    void aFalseConditionAlsoDropsSiblingBranchesAlreadyQueued() {
        CompiledRulePlan plan = plan(UUID.randomUUID(), "dedup", graph(
                List.of(STREAM_TRIGGER,
                        node("b2", "condition", Map.of("field", "severity", "operator", "eq", "value", "critical")),
                        node("b8", "push", Map.of("message", "hi"))),
                List.of(edge("e1", "b1", "b2"), edge("e2", "b1", "b8"))));

        ProcessingDecision decision = resolve(plan, "warning");

        assertThat(decision.pushIntents()).isEmpty();
    }

    // --- switch nodes ---------------------------------------------------------------------------

    @Test
    void aSwitchFollowsTheMatchingConditionBranchRecursively() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "dedup", switchCanvas());

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.thresholdIntents()).isEmpty();
    }

    @Test
    void aSwitchFallsBackToItsDefaultBranch() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "dedup", switchCanvas());

        ProcessingDecision decision = resolve(plan, "warning");

        assertThat(decision.dedupEnabled()).isFalse();
        assertThat(decision.thresholdIntents()).hasSize(1);
        assertThat(decision.executedRuleIds()).containsExactly(ruleId);
    }

    /** The switch ends the outer traversal, so nodes queued next to it are never visited. */
    @Test
    void aSwitchEndsTheOuterTraversal() {
        CompiledRulePlan plan = plan(UUID.randomUUID(), "dedup", graph(
                List.of(STREAM_TRIGGER,
                        node("sw", "switch", Map.of()),
                        node("thresh", "threshold", Map.of()),
                        node("b8", "push", Map.of("message", "hi"))),
                List.of(edge("e1", "b1", "sw"), edge("e2", "sw", "thresh"), edge("e3", "b1", "b8"))));

        ProcessingDecision decision = resolve(plan, "critical");

        assertThat(decision.thresholdIntents()).hasSize(1);
        assertThat(decision.pushIntents()).isEmpty();
    }

    // --- legacy fallback ------------------------------------------------------------------------

    @Test
    void legacyFallbackEnablesDefaultDedupForAnEmptyCanvas() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = plan(ruleId, "dedup", RuleGraph.empty());

        ProcessingDecision decision = resolve(plan, "major");

        assertThat(plan.legacyFallback()).isTrue();
        assertThat(decision.dedupEnabled()).isTrue();
        assertThat(decision.dedupRuleId()).isEqualTo(ruleId);
        assertThat(decision.dedupPolicy()).isEqualTo(DedupPolicy.defaults());
    }

    @Test
    void legacyFallbackUsesTheDefaultThresholdConfig() {
        UUID ruleId = UUID.randomUUID();

        ProcessingDecision decision = resolve(plan(ruleId, "threshold", RuleGraph.empty()), "major");

        assertThat(decision.thresholdIntents()).hasSize(1);
        assertThat(decision.thresholdIntents().getFirst().count()).isEqualTo(5);
        assertThat(decision.thresholdIntents().getFirst().windowMin()).isEqualTo(10);
    }

    @Test
    void legacyFallbackUsesTheFixedCorrelationConfig() {
        UUID ruleId = UUID.randomUUID();

        ProcessingDecision decision = resolve(plan(ruleId, "correlation", RuleGraph.empty()), "major");

        assertThat(decision.correlationIntents()).hasSize(1);
        assertThat(decision.correlationIntents().getFirst().policy())
                .isEqualTo(new CorrelationPolicy(2, 10, "title"));
    }

    @Test
    void anUnknownLegacyRuleTypeContributesNothing() {
        ProcessingDecision decision = resolve(plan(UUID.randomUUID(), "enrichment", RuleGraph.empty()), "major");

        assertThat(decision.dedupEnabled()).isFalse();
        assertThat(decision.thresholdIntents()).isEmpty();
        assertThat(decision.correlationIntents()).isEmpty();
    }

    /** The legacy fallback does not mark the rule as executed, so {@code last_run_at} stays put. */
    @Test
    void legacyFallbackDoesNotMarkTheRuleExecuted() {
        ProcessingDecision decision = resolve(plan(UUID.randomUUID(), "dedup", RuleGraph.empty()), "major");

        assertThat(decision.executedRuleIds()).isEmpty();
    }

    // --- several rules --------------------------------------------------------------------------

    @Test
    void theFirstDedupRuleWinsAndEveryOtherIntentAccumulates() {
        UUID firstRuleId = UUID.randomUUID();
        UUID secondRuleId = UUID.randomUUID();
        CompiledRulePlan first = plan(firstRuleId, "dedup", graph(
                List.of(STREAM_TRIGGER, node("b4", "dedup", Map.of("key", "title"))),
                List.of(edge("e1", "b1", "b4"))));
        CompiledRulePlan second = plan(secondRuleId, "dedup", graph(
                List.of(STREAM_TRIGGER, node("b4", "dedup", Map.of("key", "source_id"))),
                List.of(edge("e1", "b1", "b4"))));

        ProcessingDecision decision = traverser.resolve(
                raw("critical"), event("critical"), List.of(first, second));

        assertThat(decision.dedupRuleId()).isEqualTo(firstRuleId);
        assertThat(decision.dedupPolicy()).isEqualTo(new DedupPolicy(false, true, false));
        assertThat(decision.executedRuleIds()).containsExactlyInAnyOrder(firstRuleId, secondRuleId);
    }

    // --- helpers --------------------------------------------------------------------------------

    private ProcessingDecision resolve(CompiledRulePlan plan, String severity) {
        return traverser.resolve(raw(severity), event(severity), List.of(plan));
    }

    private CompiledRulePlan plan(UUID ruleId, String ruleType, RuleGraph graph) {
        return compiler.compile(ruleId, ruleType, graph);
    }

    private static RuleGraph conditionalDedupCanvas() {
        return graph(
                List.of(STREAM_TRIGGER,
                        node("b2", "condition", Map.of("field", "severity", "operator", "eq", "value", "critical")),
                        node("b4", "dedup", Map.of("key", "source_id + title + ci_id"))),
                List.of(edge("e1", "b1", "b2"), edge("e2", "b2", "b4")));
    }

    private static RuleGraph switchCanvas() {
        return graph(
                List.of(STREAM_TRIGGER,
                        node("sw", "switch", Map.of()),
                        node("cond", "condition", Map.of("field", "severity", "operator", "eq", "value", "critical")),
                        node("dedup", "dedup", Map.of("key", "source_id + title + ci_id")),
                        node("thresh", "threshold", Map.of())),
                List.of(edge("e1", "b1", "sw"),
                        edge("e2", "sw", "cond"),
                        edge("e3", "cond", "dedup"),
                        edgeWithLabel("e4", "sw", "thresh", "default")));
    }

    private static RuleGraph graph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        return new RuleGraph(nodes, edges);
    }

    private static Map<String, Object> node(String id, String type, Map<String, String> config) {
        return Map.of("id", id, "type", type, "config", config);
    }

    private static Map<String, Object> edge(String id, String source, String target) {
        return Map.of("id", id, "source", source, "target", target);
    }

    private static Map<String, Object> edgeWithLabel(String id, String source, String target, String label) {
        return Map.of("id", id, "source", source, "target", target, "label", label);
    }

    private static IncomingRawEvent raw(String severity) {
        return new IncomingRawEvent(
                UUID.randomUUID(), UUID.randomUUID(), "ext-1", "Disk full", "disk is full",
                severity, "new", "node-1.example", null, "{}", Instant.parse("2026-01-01T10:00:00Z"), false);
    }

    private static Event event(String severity) {
        Event event = new Event();
        event.setSeverity(severity);
        event.setTitle("Disk full");
        event.setStatus("new");
        event.setNodeFqdn("node-1.example");
        return event;
    }
}

package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.RuleEdge;
import ru.wisla.fm.processing.domain.RuleGraph;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchBranchSelectorTest {

    private SwitchBranchSelector selector;
    private RuleCanvasCompiler compiler;
    private IncomingRawEvent raw;
    private Event event;

    @BeforeEach
    void setUp() {
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        selector = new SwitchBranchSelector(conditionEvaluator);
        compiler = new RuleCanvasCompiler();
        raw = raw("critical");
        event = new Event();
        event.setSeverity("critical");
        event.setTitle("Alert");
    }

    @Test
    void selectsConditionBranchForCriticalEvent() {
        RuleGraph canvas = switchCanvas();
        CompiledRulePlan plan = compiler.compile(UUID.randomUUID(), "dedup", canvas);
        List<RuleEdge> outgoing = plan.outgoingBySource().get("sw");

        Optional<String> branch = selector.selectBranch("sw", outgoing, plan, raw, event);

        assertThat(branch).contains("cond");
    }

    @Test
    void selectsDefaultBranchForWarningEvent() {
        raw = raw("warning");
        RuleGraph canvas = switchCanvas();
        CompiledRulePlan plan = compiler.compile(UUID.randomUUID(), "threshold", canvas);
        List<RuleEdge> outgoing = plan.outgoingBySource().get("sw");

        Optional<String> branch = selector.selectBranch("sw", outgoing, plan, raw, event);

        assertThat(branch).contains("thresh");
    }

    private static RuleGraph switchCanvas() {
        return new RuleGraph(
                List.of(
                        Map.of("id", "sw", "type", "switch"),
                        Map.of("id", "cond", "type", "condition",
                                "config", Map.of("field", "severity", "operator", "eq", "value", "critical")),
                        Map.of("id", "dedup", "type", "dedup"),
                        Map.of("id", "thresh", "type", "threshold")
                ),
                List.of(
                        Map.of("id", "e1", "source", "sw", "target", "cond"),
                        Map.of("id", "e2", "source", "cond", "target", "dedup"),
                        Map.of("id", "e3", "source", "sw", "target", "thresh", "label", "default")
                )
        );
    }

    private static IncomingRawEvent raw(String severity) {
        return new IncomingRawEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ext-1",
                "Alert",
                null,
                severity,
                "new",
                null,
                null,
                "{}",
                Instant.parse("2026-01-01T10:00:00Z"),
                false);
    }
}

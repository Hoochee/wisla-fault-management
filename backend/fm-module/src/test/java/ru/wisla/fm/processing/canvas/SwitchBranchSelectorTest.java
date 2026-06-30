package ru.wisla.fm.processing.canvas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.rules.api.RuleCanvasDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchBranchSelectorTest {

    private SwitchBranchSelector selector;
    private RuleCanvasCompiler compiler;
    private RawEventEntity raw;
    private EventEntity event;

    @BeforeEach
    void setUp() {
        RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
        selector = new SwitchBranchSelector(conditionEvaluator);
        compiler = new RuleCanvasCompiler();
        raw = new RawEventEntity();
        raw.setSeverity("critical");
        raw.setTitle("Alert");
        event = new EventEntity();
        event.setSeverity("critical");
        event.setTitle("Alert");
    }

    @Test
    void selectsConditionBranchForCriticalEvent() {
        RuleCanvasDto canvas = new RuleCanvasDto(
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
        CompiledRulePlan plan = compiler.compile(UUID.randomUUID(), "dedup", canvas);
        List<CanvasEdgeView> outgoing = plan.outgoingBySource().get("sw");

        Optional<String> branch = selector.selectBranch("sw", outgoing, plan, raw, event);

        assertThat(branch).contains("cond");
    }

    @Test
    void selectsDefaultBranchForWarningEvent() {
        raw.setSeverity("warning");
        RuleCanvasDto canvas = new RuleCanvasDto(
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
        CompiledRulePlan plan = compiler.compile(UUID.randomUUID(), "threshold", canvas);
        List<CanvasEdgeView> outgoing = plan.outgoingBySource().get("sw");

        Optional<String> branch = selector.selectBranch("sw", outgoing, plan, raw, event);

        assertThat(branch).contains("thresh");
    }
}

package ru.wisla.fm.processing.canvas;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.rules.api.RuleCanvasDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCanvasCompilerTest {

    private final RuleCanvasCompiler compiler = new RuleCanvasCompiler();

    @Test
    void compileEmptyCanvasMarksLegacyFallback() {
        UUID ruleId = UUID.randomUUID();
        CompiledRulePlan plan = compiler.compile(ruleId, "dedup", new RuleCanvasDto(List.of(), List.of()));

        assertThat(plan.legacyFallback()).isTrue();
        assertThat(plan.triggerNodeId()).isNull();
    }

    @Test
    void compileFindsStreamTriggerAndEdges() {
        UUID ruleId = UUID.randomUUID();
        RuleCanvasDto canvas = new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b4", "type", "dedup", "config", Map.of("key", "source_id + title + ci_id"))
                ),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b4"))
        );

        CompiledRulePlan plan = compiler.compile(ruleId, "dedup", canvas);

        assertThat(plan.legacyFallback()).isFalse();
        assertThat(plan.triggerNodeId()).isEqualTo("b1");
        assertThat(plan.outgoingBySource().get("b1")).hasSize(1);
        assertThat(plan.nodesById().get("b4").type()).isEqualTo("dedup");
    }

    @Test
    void compileSupportsFrontendEdgeFormat() {
        RuleCanvasDto canvas = new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b5", "type", "threshold", "config", Map.of("count", "3", "windowMin", "5"))
                ),
                List.of(Map.of("from", "b1", "to", "b5"))
        );

        CompiledRulePlan plan = compiler.compile(UUID.randomUUID(), "threshold", canvas);

        assertThat(plan.outgoingBySource().get("b1")).hasSize(1);
        assertThat(plan.outgoingBySource().get("b1").getFirst().target()).isEqualTo("b5");
    }
}

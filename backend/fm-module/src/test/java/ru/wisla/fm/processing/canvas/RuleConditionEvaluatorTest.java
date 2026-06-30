package ru.wisla.fm.processing.canvas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.processing.domain.EventEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConditionEvaluatorTest {

    private RuleConditionEvaluator evaluator;
    private RawEventEntity raw;
    private EventEntity event;

    @BeforeEach
    void setUp() {
        evaluator = new RuleConditionEvaluator();
        raw = new RawEventEntity();
        raw.setSeverity("critical");
        raw.setTitle("Disk full");
        raw.setStatus("new");
        raw.setNodeFqdn("host.example.com");
        raw.setSourceId(UUID.randomUUID());

        event = new EventEntity();
        event.setSeverity("critical");
        event.setTitle("Disk full");
        event.setStatus("new");
        event.setNodeFqdn("host.example.com");
    }

    @Test
    void eqOperatorMatchesSeverity() {
        CanvasNodeView node = CanvasNodeView.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "severity", "operator", "eq", "value", "critical")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isTrue();
    }

    @Test
    void eqOperatorRejectsNonMatchingSeverity() {
        raw.setSeverity("warning");
        CanvasNodeView node = CanvasNodeView.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "severity", "operator", "eq", "value", "critical")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isFalse();
    }

    @Test
    void containsOperatorMatchesTitle() {
        CanvasNodeView node = CanvasNodeView.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "title", "operator", "contains", "value", "disk")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isTrue();
    }

    @Test
    void neOperatorMatchesNodeFqdn() {
        CanvasNodeView node = CanvasNodeView.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "nodeFqdn", "operator", "ne", "value", "other.example.com")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isTrue();
    }
}

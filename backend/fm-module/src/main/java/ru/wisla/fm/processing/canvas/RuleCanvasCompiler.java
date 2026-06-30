package ru.wisla.fm.processing.canvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import ru.wisla.fm.rules.api.RuleCanvasDto;

@Component
public class RuleCanvasCompiler {

    public CompiledRulePlan compile(UUID ruleId, String ruleType, RuleCanvasDto canvas) {
        List<Map<String, Object>> rawNodes = canvas.nodes() != null ? canvas.nodes() : List.of();
        List<Map<String, Object>> rawEdges = canvas.edges() != null ? canvas.edges() : List.of();

        if (rawNodes.isEmpty()) {
            return new CompiledRulePlan(ruleId, ruleType, true, null, Map.of(), Map.of());
        }

        Map<String, CanvasNodeView> nodesById = new HashMap<>();
        for (Map<String, Object> raw : rawNodes) {
            CanvasNodeView node = CanvasNodeView.fromMap(raw);
            if (node.id() != null && !node.id().isBlank()) {
                nodesById.put(node.id(), node);
            }
        }

        Map<String, List<CanvasEdgeView>> outgoing = new HashMap<>();
        for (Map<String, Object> raw : rawEdges) {
            CanvasEdgeView edge = CanvasEdgeView.fromMap(raw);
            if (edge.source() == null || edge.target() == null) {
                continue;
            }
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
        }
        outgoing.values().forEach(edges ->
                edges.sort(Comparator.comparing(CanvasEdgeView::id))
        );

        String triggerId = nodesById.values().stream()
                .filter(CanvasNodeView::isTriggerStream)
                .map(CanvasNodeView::id)
                .findFirst()
                .orElse(null);

        return new CompiledRulePlan(ruleId, ruleType, false, triggerId, nodesById, outgoing);
    }

    public Optional<CanvasNodeView> findTrigger(CompiledRulePlan plan) {
        if (plan.triggerNodeId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plan.nodesById().get(plan.triggerNodeId()));
    }
}

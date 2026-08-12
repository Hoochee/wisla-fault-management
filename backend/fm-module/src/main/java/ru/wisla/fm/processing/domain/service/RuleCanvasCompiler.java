package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.RuleEdge;
import ru.wisla.fm.processing.domain.RuleGraph;
import ru.wisla.fm.processing.domain.RuleNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RuleCanvasCompiler {

    public CompiledRulePlan compile(UUID ruleId, String ruleType, RuleGraph graph) {
        List<Map<String, Object>> rawNodes = graph.nodes() != null ? graph.nodes() : List.of();
        List<Map<String, Object>> rawEdges = graph.edges() != null ? graph.edges() : List.of();

        if (rawNodes.isEmpty()) {
            return new CompiledRulePlan(ruleId, ruleType, true, null, Map.of(), Map.of());
        }

        Map<String, RuleNode> nodesById = new HashMap<>();
        for (Map<String, Object> raw : rawNodes) {
            RuleNode node = RuleNode.fromMap(raw);
            if (node.id() != null && !node.id().isBlank()) {
                nodesById.put(node.id(), node);
            }
        }

        Map<String, List<RuleEdge>> outgoing = new HashMap<>();
        for (Map<String, Object> raw : rawEdges) {
            RuleEdge edge = RuleEdge.fromMap(raw);
            if (edge.source() == null || edge.target() == null) {
                continue;
            }
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
        }
        outgoing.values().forEach(edges ->
                edges.sort(Comparator.comparing(RuleEdge::id))
        );

        String triggerId = nodesById.values().stream()
                .filter(RuleNode::isTriggerStream)
                .map(RuleNode::id)
                .findFirst()
                .orElse(null);

        return new CompiledRulePlan(ruleId, ruleType, false, triggerId, nodesById, outgoing);
    }

    public Optional<RuleNode> findTrigger(CompiledRulePlan plan) {
        if (plan.triggerNodeId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plan.nodesById().get(plan.triggerNodeId()));
    }
}

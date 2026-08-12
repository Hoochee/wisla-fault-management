package ru.wisla.fm.processing.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CompiledRulePlan(
        UUID ruleId,
        String ruleType,
        boolean legacyFallback,
        String triggerNodeId,
        Map<String, RuleNode> nodesById,
        Map<String, List<RuleEdge>> outgoingBySource
) {
}

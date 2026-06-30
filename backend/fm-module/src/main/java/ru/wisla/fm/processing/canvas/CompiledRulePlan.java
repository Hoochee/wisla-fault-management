package ru.wisla.fm.processing.canvas;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CompiledRulePlan(
        UUID ruleId,
        String ruleType,
        boolean legacyFallback,
        String triggerNodeId,
        Map<String, CanvasNodeView> nodesById,
        Map<String, List<CanvasEdgeView>> outgoingBySource
) {
}

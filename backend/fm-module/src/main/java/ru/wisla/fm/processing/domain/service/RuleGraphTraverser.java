package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.DedupPolicy;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.ProcessingDecision;
import ru.wisla.fm.processing.domain.RuleEdge;
import ru.wisla.fm.processing.domain.RuleNode;
import ru.wisla.fm.processing.domain.ThresholdPolicy;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Walks the compiled rule graphs of every enabled rule and collects what they want done, ported
 * from {@code RuleCanvasEngine.resolveActions} / {@code traverseRule} / {@code applyLegacyFallback}.
 *
 * <p>Two traversal properties are deliberate and unchanged: a {@code condition} node that does not
 * match ends the whole traversal of that rule rather than only its own branch, and a {@code switch}
 * node recurses into the selected branch and then ends the outer traversal.
 */
public final class RuleGraphTraverser {

    private final RuleConditionEvaluator conditionEvaluator;
    private final SwitchBranchSelector switchBranchSelector;

    public RuleGraphTraverser(RuleConditionEvaluator conditionEvaluator,
                              SwitchBranchSelector switchBranchSelector) {
        this.conditionEvaluator = conditionEvaluator;
        this.switchBranchSelector = switchBranchSelector;
    }

    public ProcessingDecision resolve(
            IncomingRawEvent raw,
            Event event,
            List<CompiledRulePlan> compiledRules
    ) {
        ProcessingDecision.Builder builder = ProcessingDecision.builder();
        for (CompiledRulePlan plan : compiledRules) {
            if (plan.legacyFallback()) {
                applyLegacyFallback(plan.ruleId(), plan.ruleType(), builder);
                continue;
            }
            if (plan.triggerNodeId() == null) {
                continue;
            }
            traverseRule(plan.ruleId(), plan, plan.triggerNodeId(), raw, event, builder);
        }
        return builder.build();
    }

    private void applyLegacyFallback(UUID ruleId, String ruleType, ProcessingDecision.Builder builder) {
        if ("dedup".equals(ruleType)) {
            builder.enableDedup(ruleId, DedupPolicy.defaults());
        } else if ("threshold".equals(ruleType)) {
            builder.addThreshold(ruleId, ThresholdPolicy.defaults());
        } else if ("correlation".equals(ruleType)) {
            builder.addCorrelation(ruleId, new CorrelationPolicy(2, 10, "title"));
        }
    }

    private void traverseRule(
            UUID ruleId,
            CompiledRulePlan plan,
            String startNodeId,
            IncomingRawEvent raw,
            Event event,
            ProcessingDecision.Builder builder
    ) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(startNodeId);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!visited.add(nodeId)) {
                continue;
            }
            RuleNode node = plan.nodesById().get(nodeId);
            if (node == null) {
                continue;
            }

            if ("condition".equals(node.type())) {
                if (!conditionEvaluator.evaluate(node, raw, event)) {
                    return;
                }
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("switch".equals(node.type())) {
                List<RuleEdge> outgoing = plan.outgoingBySource().getOrDefault(nodeId, List.of());
                switchBranchSelector.selectBranch(nodeId, outgoing, plan, raw, event)
                        .ifPresent(branchStart -> traverseRule(ruleId, plan, branchStart, raw, event, builder));
                return;
            }

            if ("dedup".equals(node.type())) {
                builder.enableDedup(ruleId, DedupPolicy.fromKey(node.config().get("key")));
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("threshold".equals(node.type())) {
                builder.addThreshold(ruleId, ThresholdPolicy.fromNode(node));
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("correlation".equals(node.type())) {
                builder.addCorrelation(ruleId, CorrelationPolicy.fromNode(node));
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("notify".equals(node.type())) {
                builder.addNotify(
                        ruleId,
                        node.config().get("channel"),
                        node.config().get("emailAddress"));
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("push".equals(node.type())) {
                builder.addPush(ruleId, node.config().get("message"));
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("trigger".equals(node.type())) {
                enqueueTargets(plan, nodeId, queue);
            }
        }
    }

    private void enqueueTargets(CompiledRulePlan plan, String nodeId, Queue<String> queue) {
        for (RuleEdge edge : plan.outgoingBySource().getOrDefault(nodeId, List.of())) {
            queue.add(edge.target());
        }
    }
}

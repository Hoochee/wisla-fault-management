package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.RuleEdge;
import ru.wisla.fm.processing.domain.RuleNode;

import java.util.List;
import java.util.Optional;

public final class SwitchBranchSelector {

    private final RuleConditionEvaluator conditionEvaluator;

    public SwitchBranchSelector(RuleConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public Optional<String> selectBranch(
            String switchNodeId,
            List<RuleEdge> outgoing,
            CompiledRulePlan plan,
            IncomingRawEvent raw,
            Event event
    ) {
        String defaultTarget = null;
        for (RuleEdge edge : outgoing) {
            RuleNode target = plan.nodesById().get(edge.target());
            if (target == null) {
                continue;
            }
            if ("condition".equals(target.type())) {
                if (conditionEvaluator.evaluate(target, raw, event)) {
                    return Optional.of(edge.target());
                }
            } else if (edge.isDefaultBranch()) {
                defaultTarget = edge.target();
            } else if (target.isAction()) {
                return Optional.of(edge.target());
            }
        }
        return Optional.ofNullable(defaultTarget);
    }
}

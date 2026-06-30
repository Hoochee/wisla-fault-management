package ru.wisla.fm.processing.canvas;

import org.springframework.stereotype.Component;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.processing.domain.EventEntity;

import java.util.List;
import java.util.Optional;

@Component
public class SwitchBranchSelector {

    private final RuleConditionEvaluator conditionEvaluator;

    public SwitchBranchSelector(RuleConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public Optional<String> selectBranch(
            String switchNodeId,
            List<CanvasEdgeView> outgoing,
            CompiledRulePlan plan,
            RawEventEntity raw,
            EventEntity event
    ) {
        String defaultTarget = null;
        for (CanvasEdgeView edge : outgoing) {
            CanvasNodeView target = plan.nodesById().get(edge.target());
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

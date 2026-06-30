package ru.wisla.fm.processing.canvas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.rules.api.RuleCanvasDto;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

@Service
public class RuleCanvasEngine {

    private final RuleCanvasCompiler compiler;
    private final RuleConditionEvaluator conditionEvaluator;
    private final SwitchBranchSelector switchBranchSelector;
    private final ProcessingRuleRepository processingRuleRepository;
    private final ObjectMapper objectMapper;
    private final Map<UUID, CachedPlan> planCache = new HashMap<>();

    public RuleCanvasEngine(RuleCanvasCompiler compiler,
                            RuleConditionEvaluator conditionEvaluator,
                            SwitchBranchSelector switchBranchSelector,
                            ProcessingRuleRepository processingRuleRepository,
                            ObjectMapper objectMapper) {
        this.compiler = compiler;
        this.conditionEvaluator = conditionEvaluator;
        this.switchBranchSelector = switchBranchSelector;
        this.processingRuleRepository = processingRuleRepository;
        this.objectMapper = objectMapper;
    }

    public Map<UUID, CompiledRulePlan> compileRules(List<ProcessingRuleEntity> rules) {
        Map<UUID, CompiledRulePlan> compiled = new HashMap<>();
        for (ProcessingRuleEntity rule : rules) {
            compiled.put(rule.getId(), getOrCompile(rule));
        }
        return compiled;
    }

    public ProcessingDecision resolveActions(
            RawEventEntity raw,
            EventEntity event,
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

    @Transactional
    public void updateLastRunAt(Set<UUID> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (UUID ruleId : ruleIds) {
            processingRuleRepository.findById(ruleId).ifPresent(rule -> {
                rule.setLastRunAt(now);
                processingRuleRepository.save(rule);
            });
        }
    }

    private void applyLegacyFallback(UUID ruleId, String ruleType, ProcessingDecision.Builder builder) {
        if ("dedup".equals(ruleType)) {
            builder.enableDedup(ruleId, DedupConfig.defaults());
        } else if ("threshold".equals(ruleType)) {
            ThresholdConfig config = ThresholdConfig.defaults();
            builder.addThreshold(ruleId, config);
        } else if ("correlation".equals(ruleType)) {
            builder.addCorrelation(ruleId, new CorrelationConfig(2, 10, "title"));
        }
    }

    private void traverseRule(
            UUID ruleId,
            CompiledRulePlan plan,
            String startNodeId,
            RawEventEntity raw,
            EventEntity event,
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
            CanvasNodeView node = plan.nodesById().get(nodeId);
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
                List<CanvasEdgeView> outgoing = plan.outgoingBySource().getOrDefault(nodeId, List.of());
                switchBranchSelector.selectBranch(nodeId, outgoing, plan, raw, event)
                        .ifPresent(branchStart -> traverseRule(ruleId, plan, branchStart, raw, event, builder));
                return;
            }

            if ("dedup".equals(node.type())) {
                DedupConfig config = DedupConfig.fromKey(node.config().get("key"));
                builder.enableDedup(ruleId, config);
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("threshold".equals(node.type())) {
                builder.addThreshold(ruleId, ThresholdConfig.fromNode(node));
                builder.markExecuted(ruleId);
                enqueueTargets(plan, nodeId, queue);
                continue;
            }

            if ("correlation".equals(node.type())) {
                builder.addCorrelation(ruleId, CorrelationConfig.fromNode(node));
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
        for (CanvasEdgeView edge : plan.outgoingBySource().getOrDefault(nodeId, List.of())) {
            queue.add(edge.target());
        }
    }

    private CompiledRulePlan getOrCompile(ProcessingRuleEntity rule) {
        CachedPlan cached = planCache.get(rule.getId());
        if (cached != null && cached.updatedAt().equals(rule.getUpdatedAt())) {
            return cached.plan();
        }
        RuleCanvasDto canvas = parseCanvas(rule.getCanvas());
        CompiledRulePlan plan = compiler.compile(rule.getId(), rule.getRuleType(), canvas);
        planCache.put(rule.getId(), new CachedPlan(rule.getUpdatedAt(), plan));
        return plan;
    }

    private RuleCanvasDto parseCanvas(String json) {
        try {
            return objectMapper.readValue(json, RuleCanvasDto.class);
        } catch (Exception e) {
            return new RuleCanvasDto(List.of(), List.of());
        }
    }

    private record CachedPlan(Instant updatedAt, CompiledRulePlan plan) {
    }
}

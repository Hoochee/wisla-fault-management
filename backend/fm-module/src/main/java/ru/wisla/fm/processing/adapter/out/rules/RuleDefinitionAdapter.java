package ru.wisla.fm.processing.adapter.out.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.processing.application.port.out.RuleDefinitionPort;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.RuleDefinition;
import ru.wisla.fm.processing.domain.RuleGraph;
import ru.wisla.fm.processing.domain.service.RuleCanvasCompiler;
import ru.wisla.fm.rules.api.RuleCanvasDto;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The canvas-JSON parsing, the compiled-plan cache and the {@code last_run_at} write-back, moved out
 * of {@code RuleCanvasEngine} unchanged: the cache is keyed by {@code ruleId} and invalidated by
 * comparing the rule's {@code updatedAt}, an unparseable canvas degrades to an empty graph rather
 * than failing the batch, and the backing {@link HashMap} is deliberately still unsynchronized —
 * thread safety is a follow-up, not part of this change.
 */
@Component
public class RuleDefinitionAdapter implements RuleDefinitionPort {

    private final ProcessingRuleRepository processingRuleRepository;
    private final RuleCanvasCompiler compiler;
    private final ObjectMapper objectMapper;
    private final Map<UUID, CachedPlan> planCache = new HashMap<>();

    public RuleDefinitionAdapter(ProcessingRuleRepository processingRuleRepository,
                                 RuleCanvasCompiler compiler,
                                 ObjectMapper objectMapper) {
        this.processingRuleRepository = processingRuleRepository;
        this.compiler = compiler;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CompiledRulePlan> findEnabledRules() {
        List<ProcessingRuleEntity> enabledRules =
                processingRuleRepository.findByEnabledTrueOrderByCreatedAtAsc();
        List<CompiledRulePlan> plans = new ArrayList<>(enabledRules.size());
        for (ProcessingRuleEntity rule : enabledRules) {
            plans.add(getOrCompile(rule));
        }
        return plans;
    }

    @Override
    @Transactional
    public void markRun(Set<UUID> ruleIds, Instant now) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return;
        }
        for (UUID ruleId : ruleIds) {
            processingRuleRepository.findById(ruleId).ifPresent(rule -> {
                rule.setLastRunAt(now);
                processingRuleRepository.save(rule);
            });
        }
    }

    private CompiledRulePlan getOrCompile(ProcessingRuleEntity rule) {
        CachedPlan cached = planCache.get(rule.getId());
        if (cached != null && cached.updatedAt().equals(rule.getUpdatedAt())) {
            return cached.plan();
        }
        RuleDefinition definition = toDefinition(rule);
        CompiledRulePlan plan =
                compiler.compile(definition.ruleId(), definition.ruleType(), definition.graph());
        planCache.put(definition.ruleId(), new CachedPlan(definition.updatedAt(), plan));
        return plan;
    }

    private RuleDefinition toDefinition(ProcessingRuleEntity rule) {
        return new RuleDefinition(
                rule.getId(), rule.getRuleType(), parseCanvas(rule.getCanvas()), rule.getUpdatedAt());
    }

    private RuleGraph parseCanvas(String json) {
        try {
            RuleCanvasDto canvas = objectMapper.readValue(json, RuleCanvasDto.class);
            return new RuleGraph(canvas.nodes(), canvas.edges());
        } catch (Exception e) {
            return RuleGraph.empty();
        }
    }

    private record CachedPlan(Instant updatedAt, CompiledRulePlan plan) {
    }
}

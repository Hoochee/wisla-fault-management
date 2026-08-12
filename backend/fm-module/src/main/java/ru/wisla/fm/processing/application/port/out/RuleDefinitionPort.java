package ru.wisla.fm.processing.application.port.out;

import ru.wisla.fm.processing.domain.CompiledRulePlan;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The enabled processing rules, already compiled into executable plans, in {@code created_at asc}
 * order — plus the {@code last_run_at} write-back for the rules that contributed an intent.
 *
 * <p>Canvas-JSON parsing and the compiled-plan cache live in {@code RuleDefinitionAdapter}, which is
 * why this port hands out {@link CompiledRulePlan} rather than the raw
 * {@link ru.wisla.fm.processing.domain.RuleDefinition}: the cache has to sit on the compiled side to
 * keep {@code RuleCanvasEngine}'s semantics (keyed by {@code ruleId}, invalidated by comparing
 * {@code updatedAt}).
 */
public interface RuleDefinitionPort {

    List<CompiledRulePlan> findEnabledRules();

    void markRun(Set<UUID> ruleIds, Instant now);
}

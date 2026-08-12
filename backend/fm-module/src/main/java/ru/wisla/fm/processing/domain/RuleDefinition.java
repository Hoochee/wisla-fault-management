package ru.wisla.fm.processing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An enabled processing rule as the processing context sees it. {@code updatedAt} is what the
 * compiled-plan cache compares to decide whether a rule has to be recompiled.
 */
public record RuleDefinition(UUID ruleId, String ruleType, RuleGraph graph, Instant updatedAt) {
}

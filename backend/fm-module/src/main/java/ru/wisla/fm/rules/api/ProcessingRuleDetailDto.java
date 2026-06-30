package ru.wisla.fm.rules.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcessingRuleDetailDto(
        UUID id,
        String name,
        String ruleType,
        boolean enabled,
        String triggerType,
        Instant lastRunAt,
        String approvalStatus,
        String description,
        RuleCanvasDto canvas,
        List<RuleVersionDto> versions
) {
}

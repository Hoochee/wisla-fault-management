package ru.wisla.fm.rules.api;

import java.time.Instant;
import java.util.UUID;

public record ProcessingRuleDto(
        UUID id,
        String name,
        String ruleType,
        boolean enabled,
        String triggerType,
        Instant lastRunAt,
        String approvalStatus,
        String description
) {
}

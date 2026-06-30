package ru.wisla.fm.rules.api;

import jakarta.validation.constraints.NotBlank;

public record ProcessingRuleCreate(
        @NotBlank String name,
        @NotBlank String ruleType,
        @NotBlank String triggerType,
        String description,
        RuleCanvasDto canvas
) {
}

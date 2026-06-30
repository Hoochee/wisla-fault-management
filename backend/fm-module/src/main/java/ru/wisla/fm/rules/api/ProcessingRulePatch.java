package ru.wisla.fm.rules.api;

public record ProcessingRulePatch(
        String name,
        String description,
        String triggerType,
        Boolean enabled,
        RuleCanvasDto canvas
) {
}

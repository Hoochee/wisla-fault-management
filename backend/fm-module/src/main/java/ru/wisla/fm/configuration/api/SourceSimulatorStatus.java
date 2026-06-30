package ru.wisla.fm.configuration.api;

public record SourceSimulatorStatus(
        boolean reachable,
        boolean bound,
        boolean enabled,
        String sourceWebhookKey,
        String adapterWebhookUrl,
        String simulatorBaseUrl,
        Integer activeProblems,
        String message
) {
}

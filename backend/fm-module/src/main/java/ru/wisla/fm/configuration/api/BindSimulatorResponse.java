package ru.wisla.fm.configuration.api;

public record BindSimulatorResponse(
        boolean success,
        String message,
        String sourceWebhookKey,
        String adapterWebhookUrl,
        Boolean simulatorEnabled
) {
}

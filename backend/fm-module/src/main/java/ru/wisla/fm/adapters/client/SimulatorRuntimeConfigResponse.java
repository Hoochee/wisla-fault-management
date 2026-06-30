package ru.wisla.fm.adapters.client;

public record SimulatorRuntimeConfigResponse(
        boolean success,
        String adapterWebhookUrl,
        String sourceWebhookKey,
        Boolean enabled,
        String message
) {
}

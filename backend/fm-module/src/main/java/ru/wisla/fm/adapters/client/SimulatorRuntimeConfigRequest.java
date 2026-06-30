package ru.wisla.fm.adapters.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SimulatorRuntimeConfigRequest(
        String sourceWebhookKey,
        String apiKey
) {
}

package ru.wisla.fm.adapters.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SimulatorHealthResponse(
        String status,
        boolean enabled,
        @JsonProperty("source_webhook_key") String sourceWebhookKey,
        @JsonProperty("adapter_webhook_url") String adapterWebhookUrl,
        @JsonProperty("active_problems") Integer activeProblems
) {
}

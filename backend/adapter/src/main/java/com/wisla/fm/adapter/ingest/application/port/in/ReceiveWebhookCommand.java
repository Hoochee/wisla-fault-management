package com.wisla.fm.adapter.ingest.application.port.in;

import java.util.Map;

public record ReceiveWebhookCommand(
        String sourceKey,
        String headerApiKey,
        String queryApiKey,
        Map<String, Object> payload
) {
}

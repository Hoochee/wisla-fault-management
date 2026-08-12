package com.wisla.fm.adapter.ingest.application.port.in;

import com.wisla.fm.adapter.ingest.domain.SourceConfig;

import java.util.Map;

public record DeliverCommand(
        SourceConfig config,
        String ingestApiKey,
        Map<String, Object> payload
) {
}

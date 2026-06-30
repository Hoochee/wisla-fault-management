package com.wisla.fm.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wisla.adapter")
public record AdapterProperties(
        String version,
        String fmModuleBaseUrl,
        String fmModuleServiceKey,
        String internalServiceToken,
        int maxPayloadBytes,
        int bufferRetryBaseSeconds,
        long bufferRetryIntervalMs,
        boolean fmModuleHealthCheckEnabled,
        long configSyncIntervalMs
) {
}

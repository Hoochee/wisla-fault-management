package com.wisla.fm.zabbixsim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wisla.zabbix-simulator")
public record SimulatorProperties(
        boolean enabled,
        String zabbixUrl,
        /** Adapter origin only — simulator never calls fm-module directly. */
        String adapterBaseUrl,
        /** Path segment for POST /webhook/{sourceWebhookKey} on adapter. */
        String sourceWebhookKey,
        /** Optional full URL override (legacy). If blank, built from base + key. */
        String adapterWebhookUrl,
        String apiKey,
        int intervalSec,
        int initialDelaySec,
        int jitterSec,
        double recoveryProbability,
        long eventIdStart
) {
    public String resolvedAdapterWebhookUrl() {
        if (adapterWebhookUrl != null && !adapterWebhookUrl.isBlank()) {
            return adapterWebhookUrl.trim();
        }
        String base = adapterBaseUrl == null ? "http://localhost:8081" : adapterBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String key = sourceWebhookKey == null || sourceWebhookKey.isBlank()
                ? "zabbix-prod-01"
                : sourceWebhookKey.trim();
        return base + "/webhook/" + key;
    }
}

package com.wisla.fm.zabbixsim.config;

import org.springframework.stereotype.Component;

/**
 * Mutable runtime overlay on top of {@link SimulatorProperties} (env defaults).
 * Updated via {@code POST /config} and {@code POST /control} during demo flows.
 */
@Component
public class SimulatorRuntimeConfig {

    private final SimulatorProperties properties;

    private volatile Boolean enabledOverlay;
    private volatile String sourceWebhookKeyOverlay;
    private volatile String apiKeyOverlay;

    public SimulatorRuntimeConfig(SimulatorProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return enabledOverlay != null ? enabledOverlay : properties.enabled();
    }

    public void setEnabled(boolean enabled) {
        this.enabledOverlay = enabled;
    }

    public String getSourceWebhookKey() {
        if (sourceWebhookKeyOverlay != null && !sourceWebhookKeyOverlay.isBlank()) {
            return sourceWebhookKeyOverlay.trim();
        }
        return properties.sourceWebhookKey();
    }

    public String getApiKey() {
        if (apiKeyOverlay != null && !apiKeyOverlay.isBlank()) {
            return apiKeyOverlay.trim();
        }
        return properties.apiKey();
    }

    public String getAdapterBaseUrl() {
        String base = properties.adapterBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://localhost:8081";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base.trim();
    }

    public void applySourceBinding(String sourceWebhookKey, String apiKey) {
        this.sourceWebhookKeyOverlay = requireNonBlank(sourceWebhookKey, "sourceWebhookKey");
        this.apiKeyOverlay = requireNonBlank(apiKey, "apiKey");
    }

    public String resolvedAdapterWebhookUrl() {
        if (hasSourceBindingOverlay()) {
            String key = getSourceWebhookKey();
            if (key == null || key.isBlank()) {
                key = "zabbix-prod-01";
            }
            return getAdapterBaseUrl() + "/webhook/" + key.trim();
        }
        return properties.resolvedAdapterWebhookUrl();
    }

    public double recoveryProbability() {
        return properties.recoveryProbability();
    }

    public String zabbixUrl() {
        return properties.zabbixUrl();
    }

    private boolean hasSourceBindingOverlay() {
        return (sourceWebhookKeyOverlay != null && !sourceWebhookKeyOverlay.isBlank())
                || (apiKeyOverlay != null && !apiKeyOverlay.isBlank());
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

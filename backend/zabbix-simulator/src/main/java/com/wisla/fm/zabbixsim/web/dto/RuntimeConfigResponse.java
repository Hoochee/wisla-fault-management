package com.wisla.fm.zabbixsim.web.dto;

public record RuntimeConfigResponse(
        boolean success,
        String adapterWebhookUrl,
        String sourceWebhookKey,
        boolean enabled,
        String message
) {
}

package com.wisla.fm.zabbixsim.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RuntimeConfigRequest(
        @NotBlank String sourceWebhookKey,
        @NotBlank String apiKey
) {
}

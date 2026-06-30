package ru.wisla.fm.settings.api;

import java.util.Map;

public record IntegrationSettingsDto(
        WislaIntegrationDto wisla,
        ItsmIntegrationDto itsm
) {
    public record WislaIntegrationDto(Boolean enabled, String baseUrl, String apiToken) {
    }

    public record ItsmIntegrationDto(Boolean enabled, String endpoint, Map<String, Object> mapping) {
    }
}

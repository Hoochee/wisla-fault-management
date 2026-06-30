package ru.wisla.fm.settings.api;

import java.util.Map;
import java.util.UUID;

public record UserPreferencesDto(
        Boolean sidebarCollapsed,
        UUID defaultMapId,
        Map<String, Object> columnLayouts
) {
}

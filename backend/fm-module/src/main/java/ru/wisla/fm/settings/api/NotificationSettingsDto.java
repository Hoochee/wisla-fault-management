package ru.wisla.fm.settings.api;

import java.util.List;
import java.util.Map;

public record NotificationSettingsDto(
        Boolean emailEnabled,
        Boolean telegramEnabled,
        List<Map<String, Object>> rules
) {
}

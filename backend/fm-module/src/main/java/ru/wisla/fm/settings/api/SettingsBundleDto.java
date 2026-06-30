package ru.wisla.fm.settings.api;

public record SettingsBundleDto(
        ModuleSettingsDto module,
        UserPreferencesDto profile
) {
}

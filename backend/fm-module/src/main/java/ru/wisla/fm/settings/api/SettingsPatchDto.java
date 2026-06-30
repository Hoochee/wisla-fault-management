package ru.wisla.fm.settings.api;

public record SettingsPatchDto(
        ModuleSettingsPatchDto module,
        UserPreferencesDto profile
) {
}

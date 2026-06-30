package ru.wisla.fm.settings.api;

public record ModuleSettingsPatchDto(
        String timezone,
        Integer pollingIntervalSec,
        Integer autoArchiveDays,
        Integer repeatIntervalMin,
        Boolean wislaIntegration,
        Boolean itsmIntegration
) {
}

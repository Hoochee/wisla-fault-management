package ru.wisla.fm.settings.api;

public record ModuleSettingsDto(
        String timezone,
        int pollingIntervalSec,
        int autoArchiveDays,
        int repeatIntervalMin,
        boolean wislaIntegration,
        boolean itsmIntegration
) {
}

package ru.wisla.fm.settings.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsBundleDto getSettings() {
        return settingsService.getSettings();
    }

    @PatchMapping
    public SettingsBundleDto patchSettings(@RequestBody SettingsPatchDto patch) {
        return settingsService.patchSettings(patch);
    }

    @GetMapping("/notifications")
    public NotificationSettingsDto getNotificationSettings() {
        return settingsService.getNotificationSettings();
    }

    @PatchMapping("/notifications")
    public NotificationSettingsDto patchNotificationSettings(@RequestBody NotificationSettingsDto patch) {
        return settingsService.patchNotificationSettings(patch);
    }

    @GetMapping("/integrations")
    public IntegrationSettingsDto getIntegrationSettings() {
        return settingsService.getIntegrationSettings();
    }

    @PatchMapping("/integrations")
    public IntegrationSettingsDto patchIntegrationSettings(@RequestBody IntegrationSettingsDto patch) {
        return settingsService.patchIntegrationSettings(patch);
    }
}

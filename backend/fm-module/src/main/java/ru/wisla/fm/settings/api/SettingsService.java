package ru.wisla.fm.settings.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.common.security.AuthorizationService;
import ru.wisla.fm.identity.domain.UserEntity;
import ru.wisla.fm.identity.persistence.UserRepository;
import ru.wisla.fm.settings.domain.ModuleSettingsEntity;
import ru.wisla.fm.settings.persistence.ModuleSettingsRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SettingsService {

    private static final String DEFAULT_KEY = "default";

    private final ModuleSettingsRepository moduleSettingsRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public SettingsService(ModuleSettingsRepository moduleSettingsRepository,
                           UserRepository userRepository,
                           AuthorizationService authorizationService,
                           ObjectMapper objectMapper) {
        this.moduleSettingsRepository = moduleSettingsRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    public SettingsBundleDto getSettings() {
        UUID userId = authorizationService.requireUserId();
        return new SettingsBundleDto(toModuleDto(loadSettings()), loadUserPreferences(userId));
    }

    @Transactional
    public SettingsBundleDto patchSettings(SettingsPatchDto patch) {
        UUID userId = authorizationService.requireUserId();
        ModuleSettingsEntity settings = loadSettings();
        if (patch.module() != null) {
            authorizationService.requireAdmin(userId);
            applyModulePatch(settings, patch.module());
            moduleSettingsRepository.save(settings);
        }
        if (patch.profile() != null) {
            UserEntity user = userRepository.findById(userId).orElseThrow();
            user.setPreferences(toJson(mergePreferences(user.getPreferences(), patch.profile())));
            userRepository.save(user);
        }
        return new SettingsBundleDto(toModuleDto(settings), loadUserPreferences(userId));
    }

    public NotificationSettingsDto getNotificationSettings() {
        return parseNotification(loadSettings().getNotificationConfig());
    }

    @Transactional
    public NotificationSettingsDto patchNotificationSettings(NotificationSettingsDto patch) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        ModuleSettingsEntity settings = loadSettings();
        NotificationSettingsDto current = parseNotification(settings.getNotificationConfig());
        NotificationSettingsDto merged = new NotificationSettingsDto(
                patch.emailEnabled() != null ? patch.emailEnabled() : current.emailEnabled(),
                patch.telegramEnabled() != null ? patch.telegramEnabled() : current.telegramEnabled(),
                patch.rules() != null ? patch.rules() : current.rules()
        );
        settings.setNotificationConfig(toJson(merged));
        moduleSettingsRepository.save(settings);
        return merged;
    }

    public IntegrationSettingsDto getIntegrationSettings() {
        ModuleSettingsEntity settings = loadSettings();
        IntegrationSettingsDto parsed = parseIntegration(settings.getIntegrationConfig());
        return new IntegrationSettingsDto(
                new IntegrationSettingsDto.WislaIntegrationDto(
                        settings.isWislaIntegration(),
                        parsed.wisla() != null ? parsed.wisla().baseUrl() : null,
                        parsed.wisla() != null ? parsed.wisla().apiToken() : null
                ),
                new IntegrationSettingsDto.ItsmIntegrationDto(
                        settings.isItsmIntegration(),
                        parsed.itsm() != null ? parsed.itsm().endpoint() : null,
                        parsed.itsm() != null ? parsed.itsm().mapping() : Map.of()
                )
        );
    }

    @Transactional
    public IntegrationSettingsDto patchIntegrationSettings(IntegrationSettingsDto patch) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        ModuleSettingsEntity settings = loadSettings();
        IntegrationSettingsDto current = getIntegrationSettings();
        if (patch.wisla() != null) {
            if (patch.wisla().enabled() != null) {
                settings.setWislaIntegration(patch.wisla().enabled());
            }
        }
        if (patch.itsm() != null && patch.itsm().enabled() != null) {
            settings.setItsmIntegration(patch.itsm().enabled());
        }
        IntegrationSettingsDto merged = new IntegrationSettingsDto(
                new IntegrationSettingsDto.WislaIntegrationDto(
                        patch.wisla() != null && patch.wisla().enabled() != null
                                ? patch.wisla().enabled() : current.wisla().enabled(),
                        patch.wisla() != null && patch.wisla().baseUrl() != null
                                ? patch.wisla().baseUrl() : current.wisla().baseUrl(),
                        patch.wisla() != null && patch.wisla().apiToken() != null
                                ? patch.wisla().apiToken() : current.wisla().apiToken()
                ),
                new IntegrationSettingsDto.ItsmIntegrationDto(
                        patch.itsm() != null && patch.itsm().enabled() != null
                                ? patch.itsm().enabled() : current.itsm().enabled(),
                        patch.itsm() != null && patch.itsm().endpoint() != null
                                ? patch.itsm().endpoint() : current.itsm().endpoint(),
                        patch.itsm() != null && patch.itsm().mapping() != null
                                ? patch.itsm().mapping() : current.itsm().mapping()
                )
        );
        settings.setIntegrationConfig(toJson(merged));
        moduleSettingsRepository.save(settings);
        return merged;
    }

    ModuleSettingsEntity loadSettings() {
        return moduleSettingsRepository.findBySettingsKey(DEFAULT_KEY)
                .orElseGet(() -> moduleSettingsRepository.save(new ModuleSettingsEntity()));
    }

    private void applyModulePatch(ModuleSettingsEntity settings, ModuleSettingsPatchDto patch) {
        if (patch.timezone() != null) {
            settings.setTimezone(patch.timezone());
        }
        if (patch.pollingIntervalSec() != null) {
            settings.setPollingIntervalSec(Math.min(Math.max(patch.pollingIntervalSec(), 5), 300));
        }
        if (patch.autoArchiveDays() != null) {
            settings.setAutoArchiveDays(patch.autoArchiveDays());
        }
        if (patch.repeatIntervalMin() != null) {
            settings.setRepeatIntervalMin(patch.repeatIntervalMin());
        }
        if (patch.wislaIntegration() != null) {
            settings.setWislaIntegration(patch.wislaIntegration());
        }
        if (patch.itsmIntegration() != null) {
            settings.setItsmIntegration(patch.itsmIntegration());
        }
    }

    private ModuleSettingsDto toModuleDto(ModuleSettingsEntity settings) {
        return new ModuleSettingsDto(
                settings.getTimezone(),
                settings.getPollingIntervalSec(),
                settings.getAutoArchiveDays(),
                settings.getRepeatIntervalMin(),
                settings.isWislaIntegration(),
                settings.isItsmIntegration()
        );
    }

    private UserPreferencesDto loadUserPreferences(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> parsePreferences(user.getPreferences()))
                .orElse(new UserPreferencesDto(null, null, Map.of()));
    }

    private UserPreferencesDto parsePreferences(String json) {
        try {
            return objectMapper.readValue(json, UserPreferencesDto.class);
        } catch (Exception e) {
            return new UserPreferencesDto(null, null, Map.of());
        }
    }

    private Map<String, Object> mergePreferences(String currentJson, UserPreferencesDto patch) {
        Map<String, Object> current = parseMap(currentJson);
        if (patch.sidebarCollapsed() != null) {
            current.put("sidebarCollapsed", patch.sidebarCollapsed());
        }
        if (patch.defaultMapId() != null) {
            current.put("defaultMapId", patch.defaultMapId().toString());
        }
        if (patch.columnLayouts() != null) {
            current.put("columnLayouts", patch.columnLayouts());
        }
        return current;
    }

    private NotificationSettingsDto parseNotification(String json) {
        try {
            NotificationSettingsDto parsed = objectMapper.readValue(json, NotificationSettingsDto.class);
            return new NotificationSettingsDto(
                    parsed.emailEnabled() != null ? parsed.emailEnabled() : false,
                    parsed.telegramEnabled() != null ? parsed.telegramEnabled() : false,
                    parsed.rules() != null ? parsed.rules() : List.of()
            );
        } catch (Exception e) {
            return new NotificationSettingsDto(false, false, List.of());
        }
    }

    private IntegrationSettingsDto parseIntegration(String json) {
        try {
            return objectMapper.readValue(json, IntegrationSettingsDto.class);
        } catch (Exception e) {
            return new IntegrationSettingsDto(
                    new IntegrationSettingsDto.WislaIntegrationDto(false, null, null),
                    new IntegrationSettingsDto.ItsmIntegrationDto(false, null, Map.of())
            );
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON value");
        }
    }
}

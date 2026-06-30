package ru.wisla.fm.settings.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "module_settings")
public class ModuleSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "settings_key", nullable = false, unique = true, length = 32)
    private String settingsKey = "default";

    @Column(nullable = false, length = 64)
    private String timezone = "Europe/Moscow";

    @Column(name = "polling_interval_sec", nullable = false)
    private int pollingIntervalSec = 60;

    @Column(name = "auto_archive_days", nullable = false)
    private int autoArchiveDays = 30;

    @Column(name = "repeat_interval_min", nullable = false)
    private int repeatIntervalMin = 15;

    @Column(name = "wisla_integration", nullable = false)
    private boolean wislaIntegration = false;

    @Column(name = "itsm_integration", nullable = false)
    private boolean itsmIntegration = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_config", columnDefinition = "jsonb", nullable = false)
    private String notificationConfig = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "integration_config", columnDefinition = "jsonb", nullable = false)
    private String integrationConfig = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSettingsKey() {
        return settingsKey;
    }

    public void setSettingsKey(String settingsKey) {
        this.settingsKey = settingsKey;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getPollingIntervalSec() {
        return pollingIntervalSec;
    }

    public void setPollingIntervalSec(int pollingIntervalSec) {
        this.pollingIntervalSec = pollingIntervalSec;
    }

    public int getAutoArchiveDays() {
        return autoArchiveDays;
    }

    public void setAutoArchiveDays(int autoArchiveDays) {
        this.autoArchiveDays = autoArchiveDays;
    }

    public int getRepeatIntervalMin() {
        return repeatIntervalMin;
    }

    public void setRepeatIntervalMin(int repeatIntervalMin) {
        this.repeatIntervalMin = repeatIntervalMin;
    }

    public boolean isWislaIntegration() {
        return wislaIntegration;
    }

    public void setWislaIntegration(boolean wislaIntegration) {
        this.wislaIntegration = wislaIntegration;
    }

    public boolean isItsmIntegration() {
        return itsmIntegration;
    }

    public void setItsmIntegration(boolean itsmIntegration) {
        this.itsmIntegration = itsmIntegration;
    }

    public String getNotificationConfig() {
        return notificationConfig;
    }

    public void setNotificationConfig(String notificationConfig) {
        this.notificationConfig = notificationConfig;
    }

    public String getIntegrationConfig() {
        return integrationConfig;
    }

    public void setIntegrationConfig(String integrationConfig) {
        this.integrationConfig = integrationConfig;
    }
}

package ru.wisla.fm.settings.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.settings.domain.ModuleSettingsEntity;

import java.util.Optional;
import java.util.UUID;

public interface ModuleSettingsRepository extends JpaRepository<ModuleSettingsEntity, UUID> {

    Optional<ModuleSettingsEntity> findBySettingsKey(String settingsKey);
}

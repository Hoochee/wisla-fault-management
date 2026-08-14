package ru.wisla.fm.admin.api;

import java.util.List;
import java.util.UUID;

public record ProductComponentAdminDto(
        UUID id,
        String code,
        String name,
        int weight,
        String influenceType,
        int criticalThreshold,
        List<UUID> ciIds
) {
}

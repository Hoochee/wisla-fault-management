package ru.wisla.fm.admin.api;

import java.util.List;
import java.util.UUID;

public record ProductComponentPatch(
        String code,
        String name,
        Integer weight,
        String influenceType,
        Integer criticalThreshold,
        List<UUID> ciIds
) {
}

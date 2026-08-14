package ru.wisla.fm.health.api;

import java.util.List;
import java.util.UUID;

public record ComponentHealthDto(
        String code,
        String name,
        int healthPercent,
        int damagePercent,
        Integer weight,
        String influenceType,
        List<UUID> ciIds
) {
}

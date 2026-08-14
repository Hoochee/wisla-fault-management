package ru.wisla.fm.health.api;

import ru.wisla.fm.admin.api.ConfigurationItemDto;
import ru.wisla.fm.processing.api.EventDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductHealthDetailDto(
        UUID id,
        String name,
        String tenant,
        String site,
        String maxSeverity,
        int activeEventCount,
        List<UUID> ciIds,
        List<String> tags,
        int healthPercent,
        int damagePercent,
        List<ComponentHealthDto> components,
        SankeyDto sankey,
        List<ConfigurationItemDto> configurationItems,
        List<EventDto> activeEvents,
        Map<String, Integer> severityBreakdown,
        Instant calculatedAt,
        Integer minHealthToday,
        Integer maxHealthToday
) {
}

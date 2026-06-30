package ru.wisla.fm.health.api;

import ru.wisla.fm.admin.api.ConfigurationItemDto;
import ru.wisla.fm.processing.api.EventDto;

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
        List<ConfigurationItemDto> configurationItems,
        List<EventDto> activeEvents,
        Map<String, Integer> severityBreakdown
) {
    public static ProductHealthDetailDto from(ProductHealthDto health,
                                              List<ConfigurationItemDto> configurationItems,
                                              List<EventDto> activeEvents,
                                              Map<String, Integer> severityBreakdown) {
        return new ProductHealthDetailDto(
                health.id(),
                health.name(),
                health.tenant(),
                health.site(),
                health.maxSeverity(),
                health.activeEventCount(),
                health.ciIds(),
                health.tags(),
                configurationItems,
                activeEvents,
                severityBreakdown
        );
    }
}

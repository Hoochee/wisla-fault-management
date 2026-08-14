package ru.wisla.fm.health.domain;

import java.util.List;
import java.util.UUID;

public record ComponentDraft(
        String code,
        String name,
        int weight,
        String influenceType,
        Integer criticalThreshold,
        List<UUID> ciIds,
        int sortOrder
) {
}

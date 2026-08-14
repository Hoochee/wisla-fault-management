package ru.wisla.fm.health.domain;

import java.util.List;
import java.util.UUID;

public record ComponentNode(
        UUID id,
        String code,
        String name,
        int weight,
        InfluenceType influenceType,
        int criticalThreshold,
        int sortOrder,
        List<CiMembership> cis
) {
}

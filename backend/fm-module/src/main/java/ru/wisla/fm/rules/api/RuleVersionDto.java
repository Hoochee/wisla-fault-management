package ru.wisla.fm.rules.api;

import java.time.Instant;
import java.util.UUID;

public record RuleVersionDto(
        UUID id,
        int versionNumber,
        Instant createdAt,
        String createdByUserName,
        String comment,
        RuleCanvasDto canvas
) {
}

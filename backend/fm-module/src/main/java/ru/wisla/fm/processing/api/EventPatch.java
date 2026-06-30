package ru.wisla.fm.processing.api;

import java.util.List;
import java.util.UUID;

public record EventPatch(
        String status,
        String severity,
        UUID assignedUserId,
        String title,
        String description,
        List<String> tags,
        String itsmIncidentNumber
) {
}

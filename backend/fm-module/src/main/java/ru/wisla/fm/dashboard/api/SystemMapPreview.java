package ru.wisla.fm.dashboard.api;

import java.util.List;
import java.util.UUID;

public record SystemMapPreview(
        UUID id,
        String name,
        String query,
        boolean isSystem,
        boolean isPersonal,
        List<String> columns
) {
}

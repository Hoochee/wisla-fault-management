package ru.wisla.fm.admin.api;

import java.util.List;

public record RoleDto(
        java.util.UUID id,
        String name,
        String description,
        List<String> permissions
) {
}

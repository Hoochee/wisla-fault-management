package ru.wisla.fm.admin.api;

import java.util.List;

public record RolePatch(
        String name,
        String description,
        List<String> permissions
) {
}

package ru.wisla.fm.admin.api;

import java.util.List;
import java.util.UUID;

public record UserPatch(
        String fullName,
        String email,
        List<UUID> roleIds,
        String team,
        Boolean active,
        String password
) {
}

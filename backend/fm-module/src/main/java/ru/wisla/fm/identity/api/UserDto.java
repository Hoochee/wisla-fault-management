package ru.wisla.fm.identity.api;

import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String login,
        String fullName,
        String email,
        List<UUID> roleIds,
        String team,
        boolean active
) {
}

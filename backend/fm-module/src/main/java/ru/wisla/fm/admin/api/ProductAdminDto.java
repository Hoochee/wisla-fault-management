package ru.wisla.fm.admin.api;

import java.util.List;
import java.util.UUID;

public record ProductAdminDto(
        UUID id,
        String name,
        String code,
        String tenant,
        String site,
        List<String> tags,
        List<UUID> ciIds
) {
}

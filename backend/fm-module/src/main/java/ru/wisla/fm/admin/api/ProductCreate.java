package ru.wisla.fm.admin.api;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record ProductCreate(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String tenant,
        @NotBlank String site,
        List<String> tags,
        List<UUID> ciIds
) {
}

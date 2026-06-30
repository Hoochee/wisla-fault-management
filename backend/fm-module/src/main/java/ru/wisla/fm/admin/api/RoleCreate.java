package ru.wisla.fm.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RoleCreate(
        @NotBlank String name,
        String description,
        @NotEmpty List<String> permissions
) {
}

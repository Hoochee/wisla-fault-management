package ru.wisla.fm.admin.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record UserCreate(
        @NotBlank String login,
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotEmpty List<UUID> roleIds,
        String team,
        Boolean active
) {
}

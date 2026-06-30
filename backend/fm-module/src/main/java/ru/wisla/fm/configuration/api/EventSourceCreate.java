package ru.wisla.fm.configuration.api;

import jakarta.validation.constraints.NotBlank;

public record EventSourceCreate(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String protocol,
        @NotBlank String endpoint,
        String schedule,
        String status
) {
}

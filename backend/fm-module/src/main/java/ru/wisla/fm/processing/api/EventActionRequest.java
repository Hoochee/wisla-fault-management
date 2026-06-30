package ru.wisla.fm.processing.api;

import jakarta.validation.constraints.NotBlank;

public record EventActionRequest(
        @NotBlank String action,
        String comment
) {
}

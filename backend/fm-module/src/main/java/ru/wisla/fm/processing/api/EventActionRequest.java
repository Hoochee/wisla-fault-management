package ru.wisla.fm.processing.api;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record EventActionRequest(
        @NotBlank String action,
        String comment,
        UUID assignedUserId,
        Integer silenceMinutes
) {
}

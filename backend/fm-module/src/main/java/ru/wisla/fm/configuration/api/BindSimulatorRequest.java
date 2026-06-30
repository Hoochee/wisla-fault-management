package ru.wisla.fm.configuration.api;

import jakarta.validation.constraints.NotBlank;

public record BindSimulatorRequest(@NotBlank String ingestApiKey) {
}

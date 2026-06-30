package ru.wisla.fm.processing.api;

public record EventActionResult(
        EventDto event,
        EventActionLogDto logEntry
) {
}

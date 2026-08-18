package ru.wisla.fm.processing.application.port.in;

import ru.wisla.fm.processing.domain.ActionLogEntry;
import ru.wisla.fm.processing.domain.Event;

public record EventActionOutcome(Event event, ActionLogEntry log) {
}

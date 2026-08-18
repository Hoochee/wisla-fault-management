package ru.wisla.fm.processing.application.port.out;

import ru.wisla.fm.processing.domain.ActionLogEntry;

public interface EventActionLogPort {

    ActionLogEntry append(ActionLogEntry entry);
}

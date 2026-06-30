package ru.wisla.fm.processing.api;

import java.util.List;
import java.util.UUID;

public record EventDetailDto(
        EventDto event,
        List<EventActionLogDto> actionLogs,
        UUID rawEventId
) {
}

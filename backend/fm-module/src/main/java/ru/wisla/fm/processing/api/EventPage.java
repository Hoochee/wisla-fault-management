package ru.wisla.fm.processing.api;

import ru.wisla.fm.common.api.PageMeta;

import java.util.List;

public record EventPage(
        List<EventDto> items,
        PageMeta page
) {
}

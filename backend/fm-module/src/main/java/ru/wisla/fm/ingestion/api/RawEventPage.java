package ru.wisla.fm.ingestion.api;

import ru.wisla.fm.common.api.PageMeta;

import java.util.List;

public record RawEventPage(
        List<RawEventDto> items,
        PageMeta page
) {
}

package ru.wisla.fm.ingestion.adapter.in.web;

import ru.wisla.fm.common.api.PageMeta;
import ru.wisla.fm.ingestion.domain.RawEventListing;

import java.util.List;

public record RawEventPage(
        List<RawEventDto> items,
        PageMeta page
) {

    public static RawEventPage from(RawEventListing listing) {
        return new RawEventPage(
                listing.items().stream().map(RawEventDto::from).toList(),
                PageMeta.of(listing.page(), listing.size(), listing.total()));
    }
}

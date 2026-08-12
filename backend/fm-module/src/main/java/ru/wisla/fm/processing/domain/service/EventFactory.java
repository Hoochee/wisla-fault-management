package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.CiSnapshot;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;

/**
 * Turns a raw event into the event the console shows, lifted out of
 * {@code EventProcessingService.processRawEvent}.
 */
public final class EventFactory {

    public Event create(IncomingRawEvent raw, CiSnapshot ci) {
        return Event.fromRawEvent(raw, ci);
    }
}

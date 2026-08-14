package ru.wisla.fm.processing.adapter.out.lifecycle;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.EventClosed;
import ru.wisla.fm.processing.domain.EventCreated;
import ru.wisla.fm.processing.domain.EventUpdated;

import java.util.UUID;

@Component
public class EventLifecyclePublisher {

    private final ApplicationEventPublisher publisher;

    public EventLifecyclePublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void created(UUID eventId, UUID ciId) {
        publisher.publishEvent(new EventCreated(eventId, ciId));
    }

    public void updated(UUID eventId, UUID ciId) {
        publisher.publishEvent(new EventUpdated(eventId, ciId));
    }

    public void closed(UUID eventId, UUID ciId) {
        publisher.publishEvent(new EventClosed(eventId, ciId));
    }

    public void afterSave(boolean created, Event saved) {
        if (saved == null) {
            return;
        }
        if (isClosed(saved.getStatus())) {
            closed(saved.getId(), saved.getCiId());
        } else if (created) {
            created(saved.getId(), saved.getCiId());
        } else {
            updated(saved.getId(), saved.getCiId());
        }
    }

    public void afterStatusSave(String status, UUID eventId, UUID ciId) {
        if (isClosed(status)) {
            closed(eventId, ciId);
        } else {
            updated(eventId, ciId);
        }
    }

    private static boolean isClosed(String status) {
        return "closed".equals(status) || "archived".equals(status);
    }
}

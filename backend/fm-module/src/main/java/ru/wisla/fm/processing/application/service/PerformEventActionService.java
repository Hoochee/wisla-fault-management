package ru.wisla.fm.processing.application.service;

import ru.wisla.fm.processing.application.port.in.EventActionCommand;
import ru.wisla.fm.processing.application.port.in.EventActionOutcome;
import ru.wisla.fm.processing.application.port.in.PerformEventActionUseCase;
import ru.wisla.fm.processing.application.port.out.EventActionLogPort;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.application.port.out.UserDirectoryPort;
import ru.wisla.fm.processing.application.port.out.UserDirectoryPort.UserRef;
import ru.wisla.fm.processing.domain.ActionLogEntry;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.EventNotFoundException;
import ru.wisla.fm.processing.domain.UserNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Console duty actions. No Spring, JPA, Jackson or HTTP types — wired in {@code ProcessingConfig}.
 */
public class PerformEventActionService implements PerformEventActionUseCase {

    private final EventStorePort eventStore;
    private final EventActionLogPort actionLog;
    private final UserDirectoryPort users;
    private final Clock clock;

    public PerformEventActionService(EventStorePort eventStore,
                                     EventActionLogPort actionLog,
                                     UserDirectoryPort users,
                                     Clock clock) {
        this.eventStore = eventStore;
        this.actionLog = actionLog;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public EventActionOutcome perform(EventActionCommand command) {
        Event event = eventStore.findById(command.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        Instant now = clock.instant();
        String action = command.action();
        String userName = resolveUserName(command.actorUserId());

        switch (action) {
            case "ack" -> event.acknowledge(now, command.actorUserId());
            case "assign" -> assign(event, command.assignedUserId());
            case "silence" -> silence(event, command.silenceMinutes(), now, command.actorUserId());
            case "take" -> event.take(now, command.actorUserId());
            case "close" -> event.close(now);
            case "comment" -> {
                if (command.comment() == null || command.comment().isBlank()) {
                    throw new IllegalArgumentException("Comment is required for comment action");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }

        Event saved = eventStore.save(event);
        ActionLogEntry log = actionLog.append(new ActionLogEntry(
                null,
                saved.getId(),
                action,
                command.actorUserId(),
                userName,
                buildDetails(action, command.comment(), userName),
                blankToNull(command.comment()),
                null));
        return new EventActionOutcome(saved, log);
    }

    private void assign(Event event, UUID assignedUserId) {
        if (assignedUserId == null) {
            throw new IllegalArgumentException("assignedUserId is required for assign action");
        }
        UserRef assignee = users.findById(assignedUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!assignee.active()) {
            throw new IllegalArgumentException("Assignee is not active");
        }
        event.assignTo(assignee.id());
    }

    private void silence(Event event, Integer silenceMinutes, Instant now, UUID actorUserId) {
        if (silenceMinutes == null || silenceMinutes <= 0) {
            throw new IllegalArgumentException("silenceMinutes must be greater than 0");
        }
        event.silenceUntil(now.plus(Duration.ofMinutes(silenceMinutes)), actorUserId);
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return "system";
        }
        return users.findById(userId)
                .map(UserRef::fullName)
                .orElse("unknown");
    }

    private String buildDetails(String action, String comment, String userName) {
        return switch (action) {
            case "take" -> userName + " took the event";
            case "close" -> userName + " closed the event";
            case "comment" -> userName + " commented: " + comment;
            case "ack" -> userName + " acknowledged the event";
            case "assign" -> userName + " assigned the event";
            case "silence" -> userName + " silenced the event";
            default -> userName + " performed " + action;
        };
    }

    private static String blankToNull(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment;
    }
}

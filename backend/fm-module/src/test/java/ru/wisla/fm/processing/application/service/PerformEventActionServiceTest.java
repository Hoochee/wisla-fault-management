package ru.wisla.fm.processing.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.application.port.in.EventActionCommand;
import ru.wisla.fm.processing.application.port.in.EventActionOutcome;
import ru.wisla.fm.processing.application.port.out.EventActionLogPort;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.application.port.out.UserDirectoryPort;
import ru.wisla.fm.processing.application.port.out.UserDirectoryPort.UserRef;
import ru.wisla.fm.processing.domain.ActionLogEntry;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.UserNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring-free tests of duty actions against fakes of {@link EventStorePort},
 * {@link EventActionLogPort} and {@link UserDirectoryPort}.
 */
class PerformEventActionServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COLLEAGUE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INACTIVE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private FakeEventStore eventStore;
    private FakeActionLog actionLog;
    private FakeUserDirectory users;
    private Instant now;

    @BeforeEach
    void setUp() {
        eventStore = new FakeEventStore();
        actionLog = new FakeActionLog();
        users = new FakeUserDirectory();
        now = NOW;
        users.with(new UserRef(ACTOR_ID, "Ada Operator", true));
        users.with(new UserRef(COLLEAGUE_ID, "Cole Colleague", true));
        users.with(new UserRef(INACTIVE_ID, "Idle User", false));
    }

    // --- ack (event-duty-ack) -----------------------------------------------------------------

    @Test
    void ackKeepsStatusAndWritesAuditColumns() {
        Event event = activeEvent("new");
        eventStore.with(event);

        EventActionOutcome outcome = service().perform(command(event.getId(), "ack"));

        assertThat(outcome.event().getStatus()).isEqualTo("new");
        assertThat(outcome.event().getClosedAt()).isNull();
        assertThat(outcome.event().getAcknowledgedAt()).isEqualTo(NOW);
        assertThat(outcome.event().getAcknowledgedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(outcome.event().getSilencedUntil()).isNull();
        assertThat(outcome.log().action()).isEqualTo("ack");
        assertThat(actionLog.entries()).hasSize(1);
        assertThat(actionLog.entries().getFirst().action()).isEqualTo("ack");
    }

    @Test
    void repeatAckUpdatesTimestamp() {
        Event event = activeEvent("new");
        event.acknowledge(NOW, ACTOR_ID);
        eventStore.with(event);
        now = NOW.plus(Duration.ofMinutes(5));

        EventActionOutcome outcome = service().perform(command(event.getId(), "ack"));

        assertThat(outcome.event().getAcknowledgedAt()).isEqualTo(now);
        assertThat(outcome.event().getAcknowledgedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(outcome.event().getStatus()).isEqualTo("new");
        assertThat(actionLog.entries()).hasSize(1);
        assertThat(actionLog.entries().getFirst().action()).isEqualTo("ack");
    }

    @Test
    void ackOnClosedOrArchivedIsRejected() {
        Event closed = activeEvent("closed");
        closed.setClosedAt(NOW.minusSeconds(60));
        eventStore.with(closed);

        assertThatThrownBy(() -> service().perform(command(closed.getId(), "ack")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(closed.getAcknowledgedAt()).isNull();
        assertThat(actionLog.entries()).isEmpty();

        Event archived = activeEvent("archived");
        eventStore.with(archived);

        assertThatThrownBy(() -> service().perform(command(archived.getId(), "ack")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(archived.getAcknowledgedAt()).isNull();
        assertThat(actionLog.entries()).isEmpty();
    }

    // --- assign / take / comment (event-duty-assign, event-duty-comment-ui) -------------------

    @Test
    void assignColleagueKeepsStatus() {
        Event event = activeEvent("new");
        eventStore.with(event);

        EventActionOutcome outcome = service().perform(new EventActionCommand(
                event.getId(), "assign", ACTOR_ID, null, COLLEAGUE_ID, null));

        assertThat(outcome.event().getAssignedUserId()).isEqualTo(COLLEAGUE_ID);
        assertThat(outcome.event().getStatus()).isEqualTo("new");
        assertThat(outcome.event().getTakenAt()).isNull();
        assertThat(outcome.log().action()).isEqualTo("assign");
    }

    @Test
    void takeStillSelfAssignsAndSetsInProgress() {
        Event event = activeEvent("new");
        eventStore.with(event);

        EventActionOutcome outcome = service().perform(command(event.getId(), "take"));

        assertThat(outcome.event().getAssignedUserId()).isEqualTo(ACTOR_ID);
        assertThat(outcome.event().getStatus()).isEqualTo("in_progress");
        assertThat(outcome.event().getTakenAt()).isEqualTo(NOW);
        assertThat(outcome.log().action()).isEqualTo("take");
    }

    @Test
    void assignWithoutAssignedUserIdIsRejected() {
        Event event = activeEvent("new");
        eventStore.with(event);

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "assign", ACTOR_ID, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getAssignedUserId()).isNull();
        assertThat(actionLog.entries()).isEmpty();
    }

    @Test
    void unknownAssigneeIsRejected() {
        Event event = activeEvent("new");
        eventStore.with(event);
        UUID unknown = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "assign", ACTOR_ID, null, unknown, null)))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(event.getAssignedUserId()).isNull();
        assertThat(actionLog.entries()).isEmpty();
    }

    @Test
    void inactiveAssigneeIsRejected() {
        Event event = activeEvent("new");
        eventStore.with(event);

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "assign", ACTOR_ID, null, INACTIVE_ID, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getAssignedUserId()).isNull();
        assertThat(actionLog.entries()).isEmpty();
    }

    @Test
    void blankCommentIsRejected() {
        Event event = activeEvent("new");
        eventStore.with(event);

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "comment", ACTOR_ID, "  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "comment", ACTOR_ID, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(actionLog.entries()).isEmpty();
    }

    // --- silence (event-duty-silence) ---------------------------------------------------------

    @Test
    void silenceSetsSilencedUntilAndKeepsProblem() {
        Event event = activeEvent("new");
        event.setSeverity("critical");
        eventStore.with(event);

        EventActionOutcome outcome = service().perform(new EventActionCommand(
                event.getId(), "silence", ACTOR_ID, null, null, 30));

        assertThat(outcome.event().getStatus()).isEqualTo("new");
        assertThat(outcome.event().getSeverity()).isEqualTo("critical");
        assertThat(outcome.event().getClosedAt()).isNull();
        assertThat(outcome.event().getAcknowledgedAt()).isNull();
        assertThat(outcome.event().getSilencedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(outcome.event().getSilencedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(outcome.log().action()).isEqualTo("silence");
    }

    @Test
    void silenceMinutesMustBePositive() {
        Event event = activeEvent("new");
        eventStore.with(event);

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "silence", ACTOR_ID, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "silence", ACTOR_ID, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                event.getId(), "silence", ACTOR_ID, null, null, -5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getSilencedUntil()).isNull();
        assertThat(actionLog.entries()).isEmpty();
    }

    @Test
    void silenceOnClosedOrArchivedIsRejected() {
        Event closed = activeEvent("closed");
        eventStore.with(closed);

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                closed.getId(), "silence", ACTOR_ID, null, null, 15)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(closed.getSilencedUntil()).isNull();

        Event archived = activeEvent("archived");
        eventStore.with(archived);

        assertThatThrownBy(() -> service().perform(new EventActionCommand(
                archived.getId(), "silence", ACTOR_ID, null, null, 15)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(archived.getSilencedUntil()).isNull();
        assertThat(actionLog.entries()).isEmpty();
    }

    @Test
    void takeAndCloseRemainAllowedOnASilencedEvent() {
        Event takeTarget = activeEvent("new");
        takeTarget.silenceUntil(NOW.plus(Duration.ofMinutes(30)), ACTOR_ID);
        eventStore.with(takeTarget);

        EventActionOutcome taken = service().perform(command(takeTarget.getId(), "take"));
        assertThat(taken.event().getStatus()).isEqualTo("in_progress");
        assertThat(taken.event().getAssignedUserId()).isEqualTo(ACTOR_ID);
        assertThat(taken.event().isSilenced(NOW)).isTrue();

        Event closeTarget = activeEvent("new");
        closeTarget.silenceUntil(NOW.plus(Duration.ofMinutes(30)), ACTOR_ID);
        eventStore.with(closeTarget);

        EventActionOutcome closed = service().perform(command(closeTarget.getId(), "close"));
        assertThat(closed.event().getStatus()).isEqualTo("closed");
        assertThat(closed.event().getClosedAt()).isEqualTo(NOW);
    }

    private PerformEventActionService service() {
        return new PerformEventActionService(
                eventStore, actionLog, users, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static EventActionCommand command(UUID eventId, String action) {
        return new EventActionCommand(eventId, action, ACTOR_ID, null, null, null);
    }

    private static Event activeEvent(String status) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setStatus(status);
        event.setSeverity("critical");
        event.setTitle("Disk usage above 90%");
        event.setSourceAt(NOW.minusSeconds(120));
        return event;
    }

    private static final class FakeEventStore implements EventStorePort {
        private final Map<UUID, Event> byId = new HashMap<>();

        FakeEventStore with(Event event) {
            byId.put(event.getId(), event);
            return this;
        }

        @Override
        public Event save(Event event) {
            byId.put(event.getId(), event);
            return event;
        }

        @Override
        public Optional<Event> findById(UUID eventId) {
            return Optional.ofNullable(byId.get(eventId));
        }

        @Override
        public Optional<Event> findActiveDuplicate(DedupKey key) {
            return Optional.empty();
        }

        @Override
        public long countRecentBySeverity(UUID sourceId, UUID ciId, String severity, Instant since) {
            return 0;
        }

        @Override
        public boolean existsRecentByTitle(UUID sourceId, UUID ciId, String title, Instant since) {
            return false;
        }

        @Override
        public List<Event> findWindow(Event processedEvent, String matchField, Instant since) {
            return List.of();
        }
    }

    private static final class FakeActionLog implements EventActionLogPort {
        private final List<ActionLogEntry> entries = new ArrayList<>();

        @Override
        public ActionLogEntry append(ActionLogEntry entry) {
            ActionLogEntry persisted = entry.withPersisted(UUID.randomUUID(), Instant.parse("2026-02-01T12:00:01Z"));
            entries.add(persisted);
            return persisted;
        }

        List<ActionLogEntry> entries() {
            return List.copyOf(entries);
        }
    }

    private static final class FakeUserDirectory implements UserDirectoryPort {
        private final Map<UUID, UserRef> byId = new HashMap<>();

        FakeUserDirectory with(UserRef user) {
            byId.put(user.id(), user);
            return this;
        }

        @Override
        public Optional<UserRef> findById(UUID userId) {
            return Optional.ofNullable(byId.get(userId));
        }
    }
}

package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.CiSnapshot;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the raw-event to event field mapping lifted out of {@code EventProcessingService}.
 */
class EventFactoryTest {

    private static final UUID SOURCE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID RAW_EVENT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID CI_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant SOURCE_AT = Instant.parse("2026-01-01T10:00:00Z");

    private final EventFactory factory = new EventFactory();

    @Test
    void copiesTheRawEventFieldsOntoANewEvent() {
        Event event = factory.create(raw(), null);

        assertThat(event.getStatus()).isEqualTo("new");
        assertThat(event.getSeverity()).isEqualTo("major");
        assertThat(event.getTitle()).isEqualTo("Interface down");
        assertThat(event.getDescription()).isEqualTo("eth0 is down");
        assertThat(event.getSourceId()).isEqualTo(SOURCE_ID);
        assertThat(event.getNodeFqdn()).isEqualTo("node-1.example");
        assertThat(event.getRawEventId()).isEqualTo(RAW_EVENT_ID);
        assertThat(event.getSourceAt()).isEqualTo(SOURCE_AT);
        assertThat(event.getAttributes()).isEqualTo("{\"host\":\"node-1\"}");
    }

    /** The raw event's status is deliberately not carried over: a new event always starts as "new". */
    @Test
    void statusIsAlwaysNewEvenWhenTheRawEventIsClosed() {
        IncomingRawEvent closed = new IncomingRawEvent(
                RAW_EVENT_ID, SOURCE_ID, "ext-1", "Interface down", "eth0 is down",
                "major", "closed", "node-1.example", null, "{}", SOURCE_AT, false);

        assertThat(factory.create(closed, null).getStatus()).isEqualTo("new");
    }

    @Test
    void withoutAConfigurationItemTheTopologyFieldsStayUnset() {
        Event event = factory.create(raw(), null);

        assertThat(event.getCiId()).isNull();
        assertThat(event.getSystemName()).isNull();
        assertThat(event.getSubsystemName()).isNull();
    }

    @Test
    void aConfigurationItemContributesCiIdSystemAndSubsystem() {
        Event event = factory.create(raw(), new CiSnapshot(CI_ID, "node-1.example", "Billing", "Payments"));

        assertThat(event.getCiId()).isEqualTo(CI_ID);
        assertThat(event.getSystemName()).isEqualTo("Billing");
        assertThat(event.getSubsystemName()).isEqualTo("Payments");
    }

    @Test
    void countersAndJsonColumnsStartAtTheirDefaults() {
        Event event = factory.create(raw(), null);

        assertThat(event.getRepeatCount()).isEqualTo(1);
        assertThat(event.getTags()).isEqualTo("[]");
        assertThat(event.getLastRepeatAt()).isNull();
        assertThat(event.getRootEventId()).isNull();
        assertThat(event.getId()).isNull();
    }

    private static IncomingRawEvent raw() {
        return new IncomingRawEvent(
                RAW_EVENT_ID,
                SOURCE_ID,
                "ext-1",
                "Interface down",
                "eth0 is down",
                "major",
                "new",
                "node-1.example",
                null,
                "{\"host\":\"node-1\"}",
                SOURCE_AT,
                false);
    }
}

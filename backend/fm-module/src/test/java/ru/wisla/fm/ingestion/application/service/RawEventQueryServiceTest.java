package ru.wisla.fm.ingestion.application.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.RawEventBatch;
import ru.wisla.fm.ingestion.domain.RawEventListing;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use-case test without a Spring context.
 */
class RawEventQueryServiceTest {

    private final UUID sourceId = UUID.randomUUID();
    private final InMemoryIngestionPorts.RawEventStore rawEventStore =
            new InMemoryIngestionPorts.RawEventStore();
    private final InMemoryIngestionPorts.EventSourceState eventSourceState =
            new InMemoryIngestionPorts.EventSourceState()
                    .with(new SourceIngestState(sourceId, "Demo source", "active"));

    private final RawEventQueryService service = new RawEventQueryService(rawEventStore, eventSourceState);

    @Test
    void filtersAndPagingArePassedThroughToTheStore() {
        service.query(sourceId, "critical", Boolean.TRUE, 2, 25);

        assertThat(rawEventStore.sourceIdArgument()).isEqualTo(sourceId);
        assertThat(rawEventStore.severityArgument()).isEqualTo("critical");
        assertThat(rawEventStore.processedArgument()).isTrue();
        assertThat(rawEventStore.pageArgument()).isEqualTo(2);
        assertThat(rawEventStore.sizeArgument()).isEqualTo(25);
    }

    @Test
    void pageSizeIsClampedBetweenOneAndFiveHundredAndPageIsNeverNegative() {
        service.query(null, null, null, -3, 0);
        assertThat(rawEventStore.pageArgument()).isZero();
        assertThat(rawEventStore.sizeArgument()).isEqualTo(1);

        service.query(null, null, null, 0, 5000);
        assertThat(rawEventStore.sizeArgument()).isEqualTo(500);

        service.query(null, null, null, 0, 50);
        assertThat(rawEventStore.sizeArgument()).isEqualTo(50);
    }

    @Test
    void itemsKeepTheStoreOrderAndCarryTheResolvedSourceName() {
        RawEvent newer = rawEvent("newer", Instant.parse("2026-08-03T12:00:00Z"));
        RawEvent older = rawEvent("older", Instant.parse("2026-08-03T11:00:00Z"));
        rawEventStore.withPage(new RawEventBatch(List.of(newer, older), 0, 50, 2));

        RawEventListing listing = service.query(null, null, null, 0, 50);

        assertThat(listing.items())
                .extracting(item -> item.rawEvent().externalId())
                .containsExactly("newer", "older");
        assertThat(listing.items())
                .extracting(RawEventListing.Item::sourceName)
                .containsOnly("Demo source");
        assertThat(listing.items().getFirst().rawEvent().createdAt())
                .isAfter(listing.items().getLast().rawEvent().createdAt());
    }

    @Test
    void pagingMetadataIsTakenFromTheStoreResult() {
        rawEventStore.withPage(new RawEventBatch(List.of(rawEvent("a", Instant.now())), 3, 10, 137));

        RawEventListing listing = service.query(null, null, null, 3, 10);

        assertThat(listing.page()).isEqualTo(3);
        assertThat(listing.size()).isEqualTo(10);
        assertThat(listing.total()).isEqualTo(137);
    }

    @Test
    void anUnknownSourceYieldsANullSourceName() {
        rawEventStore.withPage(new RawEventBatch(
                List.of(new RawEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "orphan",
                        "Title",
                        null,
                        "major",
                        "new",
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        Instant.now(),
                        null,
                        false,
                        null,
                        null,
                        Instant.now(),
                        Instant.now())),
                0,
                50,
                1));

        RawEventListing listing = service.query(null, null, null, 0, 50);

        assertThat(listing.items().getFirst().sourceName()).isNull();
    }

    private RawEvent rawEvent(String externalId, Instant createdAt) {
        return new RawEvent(
                UUID.randomUUID(),
                sourceId,
                externalId,
                "Title " + externalId,
                null,
                "major",
                "new",
                "host.example",
                null,
                Map.of(),
                Map.of(),
                createdAt,
                UUID.randomUUID(),
                false,
                null,
                null,
                createdAt,
                createdAt);
    }
}

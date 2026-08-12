package ru.wisla.fm.ingestion.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import ru.wisla.fm.ingestion.adapter.in.web.IngestRequest;
import ru.wisla.fm.ingestion.application.port.in.IngestCommand;
import ru.wisla.fm.ingestion.application.port.in.IngestEventsUseCase;
import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.domain.IngestOutcome;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Listener skip / redelivery policy. Uses hand-written port fakes (no Mockito) for JDK 25
 * compatibility. The unknown/inactive source check now comes from {@link EventSourceStatePort}
 * instead of the {@code EventSourceRepository} the listener used to inject; the observable
 * commit/skip/propagate policy is unchanged.
 */
class RawEventKafkaListenerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void unparseablePayloadIsSkipped() {
        List<IngestCommand> ingested = new ArrayList<>();
        RawEventKafkaListener listener = listenerWith(id -> Optional.empty(), ingested::add);

        assertThatCode(() -> listener.handle("not-json")).doesNotThrowAnyException();
        assertThat(ingested).isEmpty();
    }

    @Test
    void envelopeWithoutSourceIdOrBodyIsSkipped() {
        List<IngestCommand> ingested = new ArrayList<>();
        RawEventKafkaListener listener = listenerWith(id -> Optional.empty(), ingested::add);

        String noSourceId = """
                {"schemaVersion":1,"messageId":"11111111-1111-1111-1111-111111111111",\
                "producedAt":"2026-08-03T12:00:00Z","sourceKey":"demo","body":{"heartbeat":true,"events":[]}}""";
        String noBody = """
                {"schemaVersion":1,"messageId":"11111111-1111-1111-1111-111111111111",\
                "producedAt":"2026-08-03T12:00:00Z","sourceId":"22222222-2222-2222-2222-222222222222",\
                "sourceKey":"demo"}""";

        assertThatCode(() -> listener.handle(noSourceId)).doesNotThrowAnyException();
        assertThatCode(() -> listener.handle(noBody)).doesNotThrowAnyException();
        assertThat(ingested).isEmpty();
    }

    @Test
    void unknownSourceIdSkipsWithoutIngest() throws Exception {
        UUID unknown = UUID.randomUUID();
        List<IngestCommand> ingested = new ArrayList<>();
        RawEventKafkaListener listener = listenerWith(id -> Optional.empty(), ingested::add);

        listener.handle(objectMapper.writeValueAsString(sampleEnvelope(unknown, false)));
        assertThat(ingested).isEmpty();
    }

    @Test
    void inactiveSourceSkipsWithoutIngest() throws Exception {
        UUID sourceId = UUID.randomUUID();
        List<IngestCommand> ingested = new ArrayList<>();
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.of(source(sourceId, "paused")), ingested::add);

        listener.handle(objectMapper.writeValueAsString(sampleEnvelope(sourceId, false)));
        assertThat(ingested).isEmpty();
    }

    @Test
    void successfulIngestDoesNotThrow() throws Exception {
        UUID sourceId = UUID.randomUUID();
        List<IngestCommand> ingested = new ArrayList<>();
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.of(source(sourceId, "active")), ingested::add);

        assertThatCode(() -> listener.handle(objectMapper.writeValueAsString(sampleEnvelope(sourceId, false))))
                .doesNotThrowAnyException();
        assertThat(ingested).hasSize(1);
        assertThat(ingested.getFirst().sourceId()).isEqualTo(sourceId);
        assertThat(ingested.getFirst().eventsOrEmpty()).hasSize(1);
        assertThat(ingested.getFirst().adapterVersion()).isEqualTo("1.0.0");
    }

    @Test
    void permanentIngestFailureIsSkipped() throws Exception {
        UUID sourceId = UUID.randomUUID();
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.of(source(sourceId, "active")),
                command -> {
                    throw new IllegalArgumentException("Source not found");
                });

        assertThatCode(() -> listener.handle(objectMapper.writeValueAsString(sampleEnvelope(sourceId, false))))
                .doesNotThrowAnyException();
    }

    @Test
    void transientFailurePropagatesForRedelivery() throws Exception {
        UUID sourceId = UUID.randomUUID();
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.of(source(sourceId, "active")),
                command -> {
                    throw new DataAccessResourceFailureException("db unavailable");
                });

        assertThatThrownBy(() -> listener.handle(objectMapper.writeValueAsString(sampleEnvelope(sourceId, false))))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private RawEventKafkaListener listenerWith(
            Function<UUID, Optional<SourceIngestState>> find,
            Consumer<IngestCommand> onIngest) {
        EventSourceStatePort eventSourceState = new EventSourceStatePort() {
            @Override
            public Optional<SourceIngestState> find(UUID sourceId) {
                return find.apply(sourceId);
            }

            @Override
            public void markSuccess(UUID sourceId, String adapterVersion, Instant at) {
                throw new UnsupportedOperationException("markSuccess belongs to the use case");
            }
        };

        IngestEventsUseCase ingestEvents = command -> {
            onIngest.accept(command);
            return new IngestOutcome(1, 0, List.of(UUID.randomUUID()), null);
        };

        return new RawEventKafkaListener(objectMapper, eventSourceState, ingestEvents);
    }

    private static SourceIngestState source(UUID id, String status) {
        return new SourceIngestState(id, "test", status);
    }

    private static RawEventEnvelope sampleEnvelope(UUID sourceId, boolean heartbeat) {
        IngestRequest body = heartbeat
                ? new IngestRequest(true, List.of(), "1.0.0", Instant.now())
                : new IngestRequest(
                        false,
                        List.of(new IngestRequest.IngestEventPayload(
                                "ext-1",
                                "Title",
                                null,
                                "major",
                                "new",
                                Instant.now(),
                                "host.example",
                                null,
                                null)),
                        "1.0.0",
                        Instant.now());
        return new RawEventEnvelope(1, UUID.randomUUID(), Instant.now(), sourceId, "demo", body);
    }
}

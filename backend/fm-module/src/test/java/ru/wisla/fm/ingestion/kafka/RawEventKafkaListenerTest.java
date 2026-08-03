package ru.wisla.fm.ingestion.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.api.IngestRequest;
import ru.wisla.fm.ingestion.api.IngestResponse;
import ru.wisla.fm.ingestion.api.IngestService;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Listener skip / redelivery policy. Uses JDK proxies (no Mockito) for JDK 25 compatibility.
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
        AtomicBoolean ingested = new AtomicBoolean(false);
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.empty(),
                (request, sourceId) -> ingested.set(true));

        assertThatCode(() -> listener.handle("not-json")).doesNotThrowAnyException();
        assertThat(ingested).isFalse();
    }

    @Test
    void unknownSourceIdSkipsWithoutIngest() throws Exception {
        UUID unknown = UUID.randomUUID();
        AtomicBoolean ingested = new AtomicBoolean(false);
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.empty(),
                (request, sourceId) -> ingested.set(true));

        listener.handle(objectMapper.writeValueAsString(sampleEnvelope(unknown, false)));
        assertThat(ingested).isFalse();
    }

    @Test
    void successfulIngestDoesNotThrow() throws Exception {
        UUID sourceId = UUID.randomUUID();
        AtomicBoolean ingested = new AtomicBoolean(false);
        EventSourceEntity active = activeSource(sourceId);
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.of(active),
                (request, sourceId1) -> ingested.set(true));

        assertThatCode(() -> listener.handle(objectMapper.writeValueAsString(sampleEnvelope(sourceId, false))))
                .doesNotThrowAnyException();
        assertThat(ingested).isTrue();
    }

    @Test
    void transientFailurePropagatesForRedelivery() throws Exception {
        UUID sourceId = UUID.randomUUID();
        EventSourceEntity active = activeSource(sourceId);
        RawEventKafkaListener listener = listenerWith(
                id -> Optional.of(active),
                (request, sourceId1) -> {
                    throw new DataAccessResourceFailureException("db unavailable");
                });

        assertThatThrownBy(() -> listener.handle(objectMapper.writeValueAsString(sampleEnvelope(sourceId, false))))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private RawEventKafkaListener listenerWith(
            Function<UUID, Optional<EventSourceEntity>> findById,
            BiConsumer<IngestRequest, UUID> onIngest) {
        EventSourceRepository sources = (EventSourceRepository)
                Proxy.newProxyInstance(
                        EventSourceRepository.class.getClassLoader(),
                        new Class<?>[] {EventSourceRepository.class},
                        (proxy, method, args) -> {
                            if ("findById".equals(method.getName()) && args != null && args.length == 1) {
                                return findById.apply((UUID) args[0]);
                            }
                            if ("toString".equals(method.getName())) {
                                return "EventSourceRepositoryProxy";
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });

        IngestService ingest = new IngestService(null, null, null, objectMapper) {
            @Override
            public IngestResponse ingest(IngestRequest request, UUID sourceId) {
                onIngest.accept(request, sourceId);
                return new IngestResponse(1, 0, List.of(UUID.randomUUID()), null);
            }
        };
        return new RawEventKafkaListener(objectMapper, sources, ingest);
    }

    private static EventSourceEntity activeSource(UUID id) {
        EventSourceEntity source = new EventSourceEntity();
        source.setId(id);
        source.setStatus("active");
        source.setName("test");
        source.setType("push_rest");
        source.setProtocol("HTTPS/REST");
        source.setEndpoint("http://localhost");
        source.setApiKeyHash("hash");
        source.setApiKeyPrefix("pref");
        return source;
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

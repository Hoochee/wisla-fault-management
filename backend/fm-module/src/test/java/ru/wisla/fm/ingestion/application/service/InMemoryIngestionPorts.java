package ru.wisla.fm.ingestion.application.service;

import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.application.port.out.ProcessRawEventBatchPort;
import ru.wisla.fm.ingestion.application.port.out.RawEventStorePort;
import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.RawEventBatch;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Hand-written in-memory outbound-port doubles for the Spring-free ingestion use-case tests.
 * No Mockito — the repo uses JDK proxies / hand-written fakes for JDK 25 compatibility.
 */
final class InMemoryIngestionPorts {

    private InMemoryIngestionPorts() {
    }

    static final class RawEventStore implements RawEventStorePort {

        private final Map<UUID, RawEvent> saved = new LinkedHashMap<>();
        private Predicate<RawEvent> failWhen = rawEvent -> false;
        private int pageArgument;
        private int sizeArgument;
        private UUID sourceIdArgument;
        private String severityArgument;
        private Boolean processedArgument;
        private RawEventBatch nextPage = new RawEventBatch(List.of(), 0, 0, 0);

        RawEventStore failOn(Predicate<RawEvent> predicate) {
            this.failWhen = predicate;
            return this;
        }

        RawEventStore withPage(RawEventBatch page) {
            this.nextPage = page;
            return this;
        }

        @Override
        public UUID save(RawEvent rawEvent) {
            if (failWhen.test(rawEvent)) {
                throw new IllegalStateException("cannot store " + rawEvent.externalId());
            }
            UUID id = UUID.randomUUID();
            saved.put(id, rawEvent);
            return id;
        }

        @Override
        public RawEventBatch findPage(UUID sourceId, String severity, Boolean processed, int page, int size) {
            this.sourceIdArgument = sourceId;
            this.severityArgument = severity;
            this.processedArgument = processed;
            this.pageArgument = page;
            this.sizeArgument = size;
            return nextPage;
        }

        List<RawEvent> savedEvents() {
            return List.copyOf(saved.values());
        }

        Map<UUID, RawEvent> savedById() {
            return Map.copyOf(saved);
        }

        int pageArgument() {
            return pageArgument;
        }

        int sizeArgument() {
            return sizeArgument;
        }

        UUID sourceIdArgument() {
            return sourceIdArgument;
        }

        String severityArgument() {
            return severityArgument;
        }

        Boolean processedArgument() {
            return processedArgument;
        }
    }

    static final class EventSourceState implements EventSourceStatePort {

        record SuccessMark(UUID sourceId, String adapterVersion, Instant at) {
        }

        private final Map<UUID, SourceIngestState> sources = new HashMap<>();
        private final List<SuccessMark> marks = new ArrayList<>();

        EventSourceState with(SourceIngestState state) {
            sources.put(state.id(), state);
            return this;
        }

        @Override
        public Optional<SourceIngestState> find(UUID sourceId) {
            return Optional.ofNullable(sources.get(sourceId));
        }

        @Override
        public void markSuccess(UUID sourceId, String adapterVersion, Instant at) {
            marks.add(new SuccessMark(sourceId, adapterVersion, at));
        }

        List<SuccessMark> marks() {
            return List.copyOf(marks);
        }
    }

    static final class ProcessRawEventBatch implements ProcessRawEventBatchPort {

        private final List<List<UUID>> invocations = new ArrayList<>();

        @Override
        public void process(List<UUID> rawEventIds) {
            invocations.add(List.copyOf(rawEventIds));
        }

        List<List<UUID>> invocations() {
            return List.copyOf(invocations);
        }
    }
}

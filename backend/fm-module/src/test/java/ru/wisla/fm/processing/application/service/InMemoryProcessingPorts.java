package ru.wisla.fm.processing.application.service;

import ru.wisla.fm.processing.application.port.out.CiLookupPort;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.application.port.out.NotificationPort;
import ru.wisla.fm.processing.application.port.out.PushNotificationPort;
import ru.wisla.fm.processing.application.port.out.RawEventStatePort;
import ru.wisla.fm.processing.application.port.out.RuleDefinitionPort;
import ru.wisla.fm.processing.domain.CiSnapshot;
import ru.wisla.fm.processing.domain.CompiledRulePlan;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hand-written in-memory outbound-port doubles for the Spring-free processing use-case tests.
 * No Mockito — the repo uses JDK proxies / hand-written fakes for JDK 25 compatibility.
 */
final class InMemoryProcessingPorts {

    private InMemoryProcessingPorts() {
    }

    static final class RawEventState implements RawEventStatePort {

        record ProcessedMark(UUID rawEventId, UUID eventId, UUID ciId) {
        }

        record ErrorMark(UUID rawEventId, UUID ciId, String message) {
        }

        private final Map<UUID, IncomingRawEvent> rawEvents = new LinkedHashMap<>();
        private final List<ProcessedMark> processedMarks = new ArrayList<>();
        private final List<ErrorMark> errorMarks = new ArrayList<>();

        RawEventState with(IncomingRawEvent rawEvent) {
            rawEvents.put(rawEvent.id(), rawEvent);
            return this;
        }

        @Override
        public Optional<IncomingRawEvent> findById(UUID rawEventId) {
            return Optional.ofNullable(rawEvents.get(rawEventId));
        }

        @Override
        public void markProcessed(UUID rawEventId, UUID eventId, UUID ciId) {
            processedMarks.add(new ProcessedMark(rawEventId, eventId, ciId));
        }

        @Override
        public void recordError(UUID rawEventId, UUID ciId, String message) {
            errorMarks.add(new ErrorMark(rawEventId, ciId, message));
        }

        List<ProcessedMark> processedMarks() {
            return List.copyOf(processedMarks);
        }

        List<ErrorMark> errorMarks() {
            return List.copyOf(errorMarks);
        }
    }

    static final class EventStore implements EventStorePort {

        record WindowQuery(UUID sourceId, UUID ciId, String matchField, String title, String severity, Instant since) {
        }

        record CountQuery(UUID sourceId, UUID ciId, String severity, Instant since) {
        }

        record ExistsQuery(UUID sourceId, UUID ciId, String title, Instant since) {
        }

        private final List<Event> saved = new ArrayList<>();
        private final Map<UUID, Event> byId = new HashMap<>();
        private final List<DedupKey> dedupKeys = new ArrayList<>();
        private final List<CountQuery> countQueries = new ArrayList<>();
        private final List<ExistsQuery> existsQueries = new ArrayList<>();
        private final List<WindowQuery> windowQueries = new ArrayList<>();
        private Optional<Event> duplicate = Optional.empty();
        private long recentCount;
        private boolean syntheticExists;
        private List<Event> window = List.of();
        private String saveFailureMessage;

        EventStore failingOnSave(String message) {
            this.saveFailureMessage = message;
            return this;
        }

        EventStore withDuplicate(Event existing) {
            this.duplicate = Optional.of(existing);
            return this;
        }

        EventStore withRecentCount(long count) {
            this.recentCount = count;
            return this;
        }

        EventStore withSyntheticExisting() {
            this.syntheticExists = true;
            return this;
        }

        EventStore withWindow(List<Event> window) {
            this.window = List.copyOf(window);
            return this;
        }

        EventStore with(Event event) {
            byId.put(event.getId(), event);
            return this;
        }

        @Override
        public Event save(Event event) {
            if (saveFailureMessage != null) {
                throw new IllegalStateException(saveFailureMessage);
            }
            if (event.getId() == null) {
                event.setId(UUID.randomUUID());
            }
            saved.add(event);
            byId.put(event.getId(), event);
            return event;
        }

        @Override
        public Optional<Event> findById(UUID eventId) {
            return Optional.ofNullable(byId.get(eventId));
        }

        @Override
        public Optional<Event> findActiveDuplicate(DedupKey key) {
            dedupKeys.add(key);
            return key.lookupRequired() ? duplicate : Optional.empty();
        }

        @Override
        public long countRecentBySeverity(UUID sourceId, UUID ciId, String severity, Instant since) {
            countQueries.add(new CountQuery(sourceId, ciId, severity, since));
            return recentCount;
        }

        @Override
        public boolean existsRecentByTitle(UUID sourceId, UUID ciId, String title, Instant since) {
            existsQueries.add(new ExistsQuery(sourceId, ciId, title, since));
            return syntheticExists;
        }

        @Override
        public List<Event> findWindow(Event processedEvent, String matchField, Instant since) {
            windowQueries.add(new WindowQuery(
                    processedEvent.getSourceId(),
                    processedEvent.getCiId(),
                    matchField,
                    processedEvent.getTitle(),
                    processedEvent.getSeverity(),
                    since));
            return window;
        }

        List<Event> saved() {
            return List.copyOf(saved);
        }

        List<DedupKey> dedupKeys() {
            return List.copyOf(dedupKeys);
        }

        List<CountQuery> countQueries() {
            return List.copyOf(countQueries);
        }

        List<ExistsQuery> existsQueries() {
            return List.copyOf(existsQueries);
        }

        List<WindowQuery> windowQueries() {
            return List.copyOf(windowQueries);
        }
    }

    static final class CiLookup implements CiLookupPort {

        private final Map<String, CiSnapshot> byFqdn = new HashMap<>();
        private final List<String> lookups = new ArrayList<>();
        private String failFqdn;
        private String failureMessage;

        CiLookup with(String fqdn, CiSnapshot snapshot) {
            byFqdn.put(fqdn, snapshot);
            return this;
        }

        CiLookup failingOn(String fqdn, String message) {
            this.failFqdn = fqdn;
            this.failureMessage = message;
            return this;
        }

        @Override
        public Optional<CiSnapshot> findOrCreateByFqdn(String fqdn) {
            lookups.add(fqdn);
            if (failFqdn != null && failFqdn.equals(fqdn)) {
                throw new IllegalStateException(failureMessage);
            }
            return Optional.ofNullable(byFqdn.get(fqdn));
        }

        List<String> lookups() {
            return List.copyOf(lookups);
        }
    }

    static final class RuleDefinitions implements RuleDefinitionPort {

        record RunMark(Set<UUID> ruleIds, Instant now) {
        }

        private final List<RunMark> runMarks = new ArrayList<>();
        private List<CompiledRulePlan> plans = List.of();
        private int lookups;

        RuleDefinitions with(CompiledRulePlan... compiledPlans) {
            this.plans = List.of(compiledPlans);
            return this;
        }

        @Override
        public List<CompiledRulePlan> findEnabledRules() {
            lookups++;
            return plans;
        }

        @Override
        public void markRun(Set<UUID> ruleIds, Instant now) {
            runMarks.add(new RunMark(Set.copyOf(ruleIds), now));
        }

        int lookups() {
            return lookups;
        }

        List<RunMark> runMarks() {
            return List.copyOf(runMarks);
        }
    }

    static final class Notifications implements NotificationPort {

        record Delivery(UUID ruleId, String channel, String emailAddress) {
        }

        private final List<Delivery> deliveries = new ArrayList<>();

        @Override
        public void notify(UUID ruleId, String channel, String emailAddress) {
            deliveries.add(new Delivery(ruleId, channel, emailAddress));
        }

        List<Delivery> deliveries() {
            return List.copyOf(deliveries);
        }
    }

    static final class PushNotifications implements PushNotificationPort {

        record Push(UUID ruleId, UUID eventId, String title, String message) {
        }

        private final List<Push> pushes = new ArrayList<>();

        @Override
        public void createPush(UUID ruleId, UUID eventId, String title, String message) {
            pushes.add(new Push(ruleId, eventId, title, message));
        }

        List<Push> pushes() {
            return List.copyOf(pushes);
        }
    }
}

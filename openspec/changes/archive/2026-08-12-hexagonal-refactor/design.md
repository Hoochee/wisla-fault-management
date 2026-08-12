## Context

`docs/adr/ADR-001-hexagonal-architecture.md` (Accepted) defines the target layering for every backend bounded context but was scoped as process-only: "it does not reorganize the existing implementation". This change is the first intentional pilot of that ADR against production code.

Current state, verified against the code:

**`backend/adapter`** (`com.wisla.fm.adapter`, Spring Boot 3.4.5, `java.version = 25`)

| Class | ADR-001 violation |
|---|---|
| `service/WebhookService` | Use case injects `ObjectMapper`, `PasswordEncoder`, `AdapterProperties`; operates directly on `@Entity SourceConfigSnapshot` / `BufferedMessage`; returns the web DTO `WebhookAcceptedResponse`; parses the body and enforces `max-payload-bytes` itself. |
| `service/FilterService` | Pure decision logic annotated `@Service`, keyed off `Map<String, Object>`. |
| `service/IngestPayloadMapper` | Domain normalization (zabbix severity, `event_value=0` → `status=closed`, probe → heartbeat) inside a `@Service`, reading `AdapterProperties` and calling `Instant.now()`. |
| `service/BufferService`, `service/BufferRetryWorker` | `@Scheduled` + `@Transactional` + Spring Data in one class; backoff logic lives on the `@Entity` (`BufferedMessage.scheduleRetry`). |
| `service/SourceConfigService` | Lookup and web-DTO mapping in one service. |
| `service/SourceConfigSyncService` | `ApplicationRunner` + `@Scheduled` + `RestClient` + `ObjectMapper` + repository, all in one class. |
| `persistence/entity/SourceConfigSnapshot`, `persistence/entity/BufferedMessage` | `@Entity` with `@JdbcTypeCode(SqlTypes.JSON)` for `filter_rules` / `payload`. |
| `kafka/RawEventPublisher` | The one correct outbound port in the codebase; implementation `RawEventKafkaPublisher`. |

**`backend/fm-module`** (`ru.wisla.fm`, same Boot/Java versions)

| Class | ADR-001 violation |
|---|---|
| `ingestion/api/IngestService` | `@Transactional` use case injects `Authentication`, `ObjectMapper`, `RawEventRepository`, `EventSourceRepository`, and calls `EventProcessingService` directly. |
| `ingestion/kafka/RawEventKafkaListener` | Inbound adapter that reads `EventSourceRepository` itself to decide unknown/inactive source. |
| `ingestion/domain/RawEventEntity` | `@Entity` inside `domain`. |
| `processing/service/EventProcessingService` | Orchestration + raw→event mapping + push-message templating; injects `cmdb.CiService`, `rules.ProcessingRuleRepository`, `notifications.*` directly. |
| `processing/service/DedupService`, `ThresholdService`, `CorrelationService` | Domain rules interleaved with Spring Data derived queries. |
| `processing/canvas/RuleCanvasEngine` | `@Service` + `@Transactional`, injects `ObjectMapper` and `ProcessingRuleRepository`, holds an unsynchronized `HashMap` plan cache, imports `rules.api.RuleCanvasDto`. |
| `processing/domain/EventEntity`, `EventActionLogEntity` | `@Entity` inside `domain`. |
| `notifications/api/NotifyStubService` | Reverse dependency: imports `processing.canvas.ProcessingDecision`. |
| `rules/api/RuleCanvasValidator` | Reverse dependency: imports `processing.canvas.CanvasNodeView` / `CanvasEdgeView`. |

Neither pom has MapStruct, ArchUnit, or Testcontainers. Tests are `@SpringBootTest` + H2 (`AbstractFmModuleTest`) and `@EmbeddedKafka`.

Constraint from the user, binding: the refactor changes **no** external behavior. Every frozen REST status code, the Kafka envelope and commit policy, the DDL, and the `ingest → processBatch` transaction boundary must be provably identical afterwards.

## Goals / Non-Goals

**Goals:**

- Apply ADR-001 layering to the ingest vertical slice (`com.wisla.fm.adapter.ingest`, `ru.wisla.fm.ingestion`) and to the processing core (`ru.wisla.fm.processing`, including the canvas rule runtime).
- Perform a full JPA split: pure domain models, `*JpaEntity` classes under `adapter/out/persistence`, hand-written mappers.
- Give every outbound side effect of an in-scope use case exactly one port in `application/port/out` and exactly one implementation in `adapter/out`.
- Make every in-scope use case testable without a Spring context, using outbound-port test doubles.
- Enforce the layering with ArchUnit in both modules, scoped so unmigrated contexts do not break the build.
- **Preserve, and prove by rule, the compile-time independence of the two microservices.**
- Keep the `ingest → processBatch` transaction boundary and every frozen contract byte-identical.

**Non-Goals:**

- Any REST, Kafka, database, or Liquibase contract change.
- **Any shared Java module, shared jar, or Maven dependency between `backend/adapter` and `backend/fm-module`.**
- MapStruct, Testcontainers migration, service split, frontend, prototype, `zabbix-simulator`.
- Migrating `identity`, `console`, `dashboard`, `admin`, `settings`, `health`, `configuration`, `cmdb`, `rules`, `notifications` to hexagonal layering.
- Fixing the `RuleCanvasEngine` plan-cache thread-safety, or optimizing any query.
- Strangler-style temporary delegating wrappers.

## Decisions

### D0. The two services stay compile-time independent — no shared module, ever

**This is the governing constraint of the whole change and outranks every convenience argument below.**

`backend/adapter` and `backend/fm-module` are two independently built, independently deployed Spring Boot applications with separate Maven coordinates (`com.wisla.fm:adapter:1.0.0`, and fm-module's own), separate databases/schemas, separate release cycles, and no Maven relationship. The refactor keeps it that way:

1. **No Maven dependency** from `backend/adapter/pom.xml` on fm-module, or vice versa. The only pom change in this entire change is `archunit-junit5` at `test` scope in each.
2. **No shared `domain` / `contracts` / `common` module or jar.** Not created, not proposed, not "for later".
3. **No shared Java class of any kind** — not the Kafka envelope, not ingest DTOs, not enums, not constant holders.
4. **Each service keeps its own copy of the wire-contract types.** This is the existing design and it is correct. Today:

   ```text
   com.wisla.fm.adapter.kafka.RawEventEnvelope   → body : Map<String, Object>
   ru.wisla.fm.ingestion.kafka.RawEventEnvelope  → body : IngestRequest (@Valid, @NotNull, @NotBlank)
   ```

   Two structurally different Java records serializing to and deserializing from one JSON shape. The producer does not need to know fm-module's validation annotations; the consumer does not need to know the producer's loose map. Both keep `schemaVersion = 1` and the same field names. After the refactor the adapter copy lives in `adapter/out/kafka` and the fm-module copy in `adapter/in/messaging` — still two files, still unrelated.

5. **`FmModuleSourceConfigPort` is a port to an external system.** `SyncSourceConfigService` needs source configuration that fm-module owns. That is an outbound port whose implementation `FmModuleSourceConfigClient` lives in `adapter/out/http` and speaks HTTP to `GET /api/v1/internal/sources` with the `X-Service-Key` header. It maps the JSON response into the adapter's **own** `SourceConfig` domain model. It never imports an fm-module type; it never sees `EventSourceEntity`.
6. **Integration surface is network-only** and unchanged: Kafka topic `fm.raw-events` (key `sourceId.toString()`, value `RawEventEnvelope` with `schemaVersion = 1`) plus the internal HTTP endpoints `GET /api/v1/internal/sources`, `GET /internal/sources/{sourceId}/config`, `POST /internal/probe`, `POST /internal/config/sync`.
7. **ArchUnit enforces it**: `noClasses().that().resideInAPackage("com.wisla.fm.adapter..").should().dependOnClassesThat().resideInAnyPackage("ru.wisla.fm..")` in the adapter module, and the mirror rule in fm-module.

**Alternative considered and explicitly rejected: extract a shared `fm-contracts` module holding `RawEventEnvelope`, `IngestRequest` and the severity/status enums, "so we do not duplicate the envelope".** Rejected. It is the single most likely way this refactor could damage the system, and hexagonal refactoring makes it especially tempting because ports and DTOs suddenly look like reusable vocabulary. Reasons:

- It couples the release cycles of two independently deployable services: a change to the shared jar forces a coordinated rebuild and redeploy of both, which is exactly the property microservice boundaries exist to prevent.
- It converts a *wire* contract (versioned, tolerant, evolvable by `schemaVersion`) into a *compile-time* contract (rigid, all-or-nothing). Rolling upgrades where producer and consumer temporarily disagree about an optional field become build failures instead of a non-event.
- The two copies are not actually identical — see the `body` type difference above — so the "duplicate" is a strawman; unifying them would force one service to adopt the other's validation and serialization concerns.
- ADR-001 already lists "A shared domain JAR between deployables" as a non-goal, and rejects "One service-wide domain and adapter layer" for weakening bounded-context boundaries.

The duplication of a handful of small records is the deliberate, documented price of independence. It is cheap: the fields are frozen, and any drift is caught by the contract regression tests (`RawEventEnvelopeCodecTest`, `RawEventEnvelopeTest`, `RawEventKafkaConsumerTest`) which assert the JSON shape on both sides.

### D1. Direct move, not a strangler with delegating wrappers

Migrate each class in place — create the new domain model, ports and mappers, wire the use case onto ports, switch the inbound adapter, delete the old class — rather than leaving `@Deprecated` delegating shims behind.

Rationale:

1. These classes have no public Java API and no external consumers. A deprecation window would preserve compatibility that nobody needs.
2. Two `@Entity` classes mapped to one table (`events`, `raw_events`, `source_config_snapshots`) during a transition period is genuinely dangerous: two Hibernate mappings of the same row inside a single transaction risk divergent snapshot state and dirty-checking conflicts, producing exactly the kind of silent `repeat_count` / `processed` drift that "no behavior change" forbids.
3. Delegating wrappers around `@Transactional` services introduce proxy-in-proxy and blur the transaction boundary — the one thing the user pinned as must-not-change.
4. ArchUnit lands in this same change. Temporary legacy classes sitting in `domain` / `application` would immediately violate the rules and require suppressions that then have to be removed.

Alternative considered: strangler-fig with `@Deprecated` delegating facades removed in a follow-up change. Rejected for the four reasons above; the safety it buys (partial rollback) is better obtained by ordering the work into small independently-green steps (see Migration Plan).

### D2. Full JPA split with hand-written mappers

Domain models are plain Java records/classes with no annotations. Every `@Entity` becomes `*JpaEntity` under `adapter/out/persistence`, keeping its table name, column names, `columnDefinition`, and `@JdbcTypeCode(SqlTypes.JSON)` **byte-for-byte**. A hand-written mapper (`SourceConfigJpaMapper`, `BufferedEventJpaMapper`, `RawEventJpaMapper`, `EventJpaMapper`) converts both ways.

Field types are preserved on both sides of the mapper to avoid changing what Hibernate writes:

| Column | Java type today | Domain type after |
|---|---|---|
| `source_config_snapshots.filter_rules` (jsonb) | `Map<String, Object>` | `FilterRules` value object, mapped from/to the same `Map` in the mapper |
| `buffered_messages.payload` (jsonb) | `Map<String, Object>` | `Map<String, Object>` on `BufferedEvent` (unchanged) |
| `raw_events.payload`, `raw_events.raw_payload` (jsonb) | `String` | `String` on `RawEvent` / `IncomingRawEvent` (unchanged) |
| `events.tags`, `events.attributes` (jsonb) | `String` | `String` on `Event` (unchanged) |

`Event.attributes` deliberately stays a JSON `String` rather than becoming a typed map: any re-serialization would risk changing key order or numeric formatting in stored jsonb.

Alternative considered: MapStruct. Rejected by user decision — an annotation processor on `java.version = 25` is an extra compatibility risk in a change whose whole point is "nothing observable changes", and the mappers are few and small.

Alternative considered: keep `@Entity` classes as the domain model (the "anemic hexagon" shortcut). Rejected — it defeats the pilot, keeps `jakarta.persistence` in `domain`, and the ArchUnit rule forbids it.

### D3. `@Transactional` moves to the inbound adapters; the boundary does not change

ADR-001 forbids Spring annotations in `application`, so `@Transactional` cannot stay on `IngestEventsService`. It moves onto the two inbound adapters:

- `IngestController.ingest(...)` — annotated `@Transactional`
- `RawEventKafkaListener.onMessage(...)` — annotated `@Transactional`

Result is identical to today: one transaction spans `ingest` **and** the `ProcessRawEventBatchPort` call, an uncaught exception rolls the whole thing back, and in the Kafka path the offset is not committed so the record is redelivered. `propagation`, `isolation` and `readOnly` are left at their defaults, exactly as now.

The internal error-swallowing behavior is also preserved verbatim: `ProcessRawEventBatchService` keeps the `try/catch` that writes `raw_events.processing_error` and returns normally, so a single failing raw event does **not** roll back the batch or the ingest.

Alternative considered: declare the transaction with AOP in `infrastructure/config` (a `@Bean` pointcut over inbound ports). Rejected as the primary mechanism — it hides the boundary from the reader of the adapter and makes the regression harder to reason about; annotation on the adapter is the smallest, most reviewable move. Recorded as the fallback if annotating the Kafka listener turns out to interact badly with the container's ack mode.

### D4. Preserve the `EventRepository` dedup / window queries verbatim

`DedupService.findActiveDuplicate` chooses between `findFirstBySourceIdAndTitleAndCiIdAndStatusIn` and `findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn`. Read carefully, the current code has three branches and the last two are the same call: when `ciId == null` — whether because `useCi = false` or because the event genuinely has no CI — it uses the `...CiIdIsNull...` variant. So with `useCi = false` the query still filters `ci_id IS NULL`. That is surprising, and it is exactly the kind of thing a refactor "fixes" by accident.

Decision: `EventPersistenceAdapter` reproduces that branching **literally**, including the redundant-looking `if (ciId == null && config.useCi())` branch, and `DedupPolicy` / `DedupKey` carry `useSource` / `useTitle` / `useCi` through unchanged. The same applies to `ThresholdService`'s 4 counting/exists queries (`countBySourceIdAndCiId...` vs `...CiIdIsNull...`, `existsBySourceIdAndCiId...` vs `...CiIdIsNull...`) and `CorrelationService`'s 6 window queries (`findBy...Title...`, `...Severity...`, `...(source only)...`, each in a `CiId` and a `CiIdIsNull` flavor).

Characterization tests for all four `useSource`/`useTitle`/`useCi` combinations against `ciId = null` and `ciId != null` are written **before** any code moves, so the surprising behavior is pinned by a test rather than by a comment.

Alternative considered: normalize the branching to "use `CiIdIsNull` only when `useCi = true` and `ciId == null`". Rejected — it changes which rows match, which changes dedup outcomes, which changes `repeat_count`. Out of scope; may be raised as a separate bug-fix change.

### D5. ArchUnit is verified against Java 25 before anything else moves

ArchUnit reads bytecode through ASM. Class file major version 69 (Java 25) may not be supported by the ArchUnit release available. Adding the dependency and running one trivial rule is therefore the **first task in the change**, before any production code is touched.

- If it parses: proceed as planned.
- If it does not: try the newest ArchUnit release.
- If that still fails: **escalate at the gate.** ArchUnit moves to a follow-up change and ADR conformance for this change is verified by code review against `design.md`. **Do not downgrade `java.version` of production code**, and do not block Phase 2 on it.

fm-module rules are scoped with `.that().resideInAPackage("ru.wisla.fm.ingestion..")` / `"ru.wisla.fm.processing.."`, never module-wide, so the ten unmigrated contexts cannot fail the build.

### D6. Remove the two reverse dependencies rather than document them away

- `notifications.api.NotifyStubService.execute(ProcessingDecision.NotifyIntent)` → signature becomes primitives `execute(UUID ruleId, String channel, String emailAddress)`. The import of `processing.canvas` disappears from `notifications`; behavior stays a no-op stub.
- `rules.api.RuleCanvasValidator` imports `processing.canvas.CanvasNodeView` / `CanvasEdgeView` → `rules` gains its own local view records. The REST contract and the validation error message strings do not change (`RuleCanvasValidatorTest` is the guard).

Alternative considered: document these as accepted deviations and leave the imports. Rejected — they are two small signature edits, and leaving them would mean the ArchUnit "no cross-context dependency" intent is only half true, which invites the shared-module thinking that D0 forbids.

### D7. Renaming `EventEntity` is a separate, logic-free step

`EventEntity` → `EventJpaEntity` touches at least eight classes outside the migration scope: `dashboard/DashboardService`, `admin/AdminService`, `configuration/SourceService`, `health/ProductHealthService`, `config/DevDataSeeder`, and the console services inside `processing` (`api/EventQueryService`, `service/EventActionService`, `service/EventUpdateService`). Those classes are **not** moved onto ports in this change — they keep using `EventJpaRepository` directly. That is a recorded, deliberate deviation: they are out of scope, and forcing ports on them would multiply the blast radius.

The rename is done as its own "rename only" step with no logic change, so review can read it as a pure diff and their existing controller tests are the regression.

### Dependency direction (ADR-001) and the six-part checklist

Dependency rule applied to both in-scope services: `domain` imports nothing from Spring, `jakarta.persistence`, Hibernate, Jackson, Kafka or `jakarta.servlet`; `application` (ports and services) depends only on `domain` plus its own port interfaces, and never on `adapter` or `infrastructure`; `adapter/in` translates transport into inbound-port calls; `adapter/out` implements outbound ports; all Spring wiring lives in `infrastructure/config`.

#### backend/adapter — context `com.wisla.fm.adapter.ingest`

**1. Use cases and inbound ports**

| Inbound port | Method | Replaces |
|---|---|---|
| `ReceiveWebhookEventUseCase` | `receive(ReceiveWebhookCommand) : DeliveryOutcome` | `WebhookService.receive(sourceKey, headerApiKey, queryApiKey, rawBody)` |
| `DeliverIngestEventUseCase` | `deliver(DeliverCommand) : DeliveryOutcome` | package-private `WebhookService.deliver(...)`, used today by `ProbeService` |
| `RetryBufferedEventsUseCase` | `retryDueMessages(Instant now)` | body of `BufferRetryWorker.retryBufferedMessages()` |
| `SyncSourceConfigUseCase` | `sync()` | `SourceConfigSyncService.syncFromFmModule()` |

Implementations: `ReceiveWebhookEventService`, `RetryBufferedEventsService`, `SyncSourceConfigService` in `application/service`. They return the domain type `DeliveryOutcome` (`forwarded` / `buffered` + optional `messageId`) and signal refusals with the domain exception `IngestRejection` carrying the frozen error code and HTTP status; the web layer maps it to `ErrorResponse`.

**2. Inbound adapters**

| Adapter | Transport | Notes |
|---|---|---|
| `adapter/in/web/WebhookController` + `WebhookPayloadReader` | HTTP `POST /webhook/{sourceKey}` | `WebhookPayloadReader` owns the `max-payload-bytes` check (413 `payload_too_large`) and JSON parsing (400 `invalid_json`) — both leave the use case |
| `adapter/in/web/InternalController` | HTTP `GET /internal/sources/{sourceId}/config`, `POST /internal/probe`, `POST /internal/config/sync` | paths, bearer-token check and DTOs unchanged |
| `adapter/in/web/HealthController` | HTTP `GET /health` | unchanged, incl. 503 when `database=down` |
| `adapter/in/web/GlobalExceptionHandler`, `dto/*` | — | moved as-is; `WebhookAcceptedResponse`, `ErrorResponse`, `HealthResponse`, `ProbeRequest`, `ProbeResponse`, `SourceConfigSnapshotDto` keep field names and `JsonInclude.NON_NULL` |
| `adapter/in/scheduler/BufferRetryScheduler` | `@Scheduled(fixedDelayString = "${wisla.adapter.buffer-retry-interval-ms:60000}")` + `@Transactional` | calls `RetryBufferedEventsUseCase` |
| `adapter/in/scheduler/SourceConfigSyncScheduler` | `ApplicationRunner` + `@Scheduled(fixedDelayString = "${wisla.adapter.config-sync-interval-ms:300000}")` | calls `SyncSourceConfigUseCase` |

**3. Outbound ports** (`application/port/out`)

| Port | Methods |
|---|---|
| `SourceConfigLookupPort` | `findBySourceKey(String) : Optional<SourceConfig>`, `findBySourceId(UUID) : Optional<SourceConfig>` |
| `SourceConfigStorePort` | `upsert(SourceConfig)` — preserves `SourceConfigSnapshot.replace(...)` semantics, i.e. `created_at` is never overwritten |
| `BufferedEventStorePort` | `save(BufferedEvent) : BufferedEvent`, `findDue(Instant) : List<BufferedEvent>`, `delete(BufferedEvent)`, `count() : long` |
| `RawEventPublisherPort` | `publish(UUID sourceId, String sourceKey, Map<String,Object> body) : PublishResult` — today's `RawEventPublisher`, contract `PublishResult(success, error, retryable)` kept 1:1, interface relocated |
| `ApiKeyVerifierPort` | `matches(String rawKey, String storedHash) : boolean` — removes `PasswordEncoder` from the use case |
| `FmModuleSourceConfigPort` | `fetchSources() : List<RemoteSourceConfig>` — **external-system port** (see D0); HTTP only |

Time is injected as `java.time.Clock` (JDK type, not a framework type) into the use-case services.

**4. Outbound adapter implementations** (`adapter/out`)

| Port | Implementation |
|---|---|
| `SourceConfigLookupPort`, `SourceConfigStorePort` | `adapter/out/persistence/SourceConfigPersistenceAdapter` over `SourceConfigSnapshotJpaRepository` + `SourceConfigJpaMapper` |
| `BufferedEventStorePort` | `adapter/out/persistence/BufferedEventPersistenceAdapter` over `BufferedMessageJpaRepository` + `BufferedEventJpaMapper` |
| `RawEventPublisherPort` | `adapter/out/kafka/RawEventKafkaPublisher` (+ `RawEventEnvelope`, `RawEventEnvelopeCodec` — the adapter's private copy of the wire type) |
| `ApiKeyVerifierPort` | `adapter/out/crypto/PasswordEncoderApiKeyVerifier` wrapping Spring Security's `PasswordEncoder` |
| `FmModuleSourceConfigPort` | `adapter/out/http/FmModuleSourceConfigClient` (`RestClient`, `X-Service-Key`), alongside the existing `FmModuleClient` |

JPA entities: `SourceConfigSnapshotJpaEntity` (`source_config_snapshots`), `BufferedMessageJpaEntity` (`buffered_messages`) — identical DDL.

Domain: `SourceConfig` (with `isExpired(Clock)`), `BufferedEvent` (with `scheduleRetry(baseSeconds, now)` implementing `base * 2^min(retryCount-1, 10)`), `FilterRules` / `FilterCondition` (`enabled`, `drop_if`, `pass_only`; operators `eq`, `ne`, `contains`, `in`, `gt`, `lt`, `exists`; dotted-path lookup), `IngestPayloadNormalizer` (takes `adapterVersion` and `Clock` as parameters), `DeliveryOutcome`, `IngestRejection`.

**5. Infrastructure wiring**: `infrastructure/config/AppConfig`, `WebClientConfig`, `AdapterProperties`, `KafkaIngestProperties`. `@ConfigurationProperties` prefixes `wisla.adapter.*` and `wisla.kafka.*` are unchanged. `AppConfig` declares the use-case services as `@Bean`s (constructor-wired with ports and a `Clock`), so `application` stays annotation-free.

**6. Spring-free use-case tests** (plain JUnit 5, in-memory port fakes, no `@SpringBootTest`)

- `ReceiveWebhookEventServiceTest` — 404 `unknown_source`; expired snapshot; 401 `invalid_source_key` on header/query mismatch; 401 `missing_api_key`; 401 `invalid_source_key` on wrong key; 403 `source_blocked`; 400 `filtered`; `forwarded`; `buffered` on retryable publish failure; 502 `ingest_rejected` on permanent failure.
- `RetryBufferedEventsServiceTest` — success deletes; permanent failure deletes; retryable reschedules via `scheduleRetry`; missing source config reschedules.
- `SyncSourceConfigServiceTest` — upsert; `blocked = status != "active"`; TTL `now + 86400s`; port failure does not throw.
- `FilterRulesTest`, `IngestPayloadNormalizerTest`, `BufferedEventTest` — domain rules (see `tasks.md` for the case list).

#### backend/fm-module — context `ru.wisla.fm.ingestion`

**1. Use cases and inbound ports**

| Inbound port | Method |
|---|---|
| `IngestEventsUseCase` | `ingest(IngestCommand) : IngestOutcome`, where `IngestCommand(sourceId, heartbeat, events, adapterVersion, receivedAt)` |
| `QueryRawEventsUseCase` | `query(page, size) : RawEventBatch` |

`Authentication` no longer reaches the use case: `IngestController` extracts the `UUID` principal placed by `SourceApiKeyAuthenticationFilter` and builds the command. The two current overloads (`ingest(request, Authentication)` and `ingest(request, UUID)`) collapse into the single command-based path already used by the Kafka listener.

**2. Inbound adapters**: `adapter/in/web/IngestController` (`POST /api/v1/ingest`, `@Transactional`, response `IngestResponse{accepted, rejected, rawEventIds, heartbeatAck}`), `adapter/in/web/RawEventController` (`GET /api/v1/raw-events`, `RawEventPage` + `PageMeta`, `createdAt desc`), `adapter/in/messaging/RawEventKafkaListener` (`@KafkaListener` on `${wisla.kafka.raw-events-topic}`, group `${spring.kafka.consumer.group-id}`, `@Transactional`) plus fm-module's own `RawEventEnvelope` copy. DTOs `IngestRequest`, `IngestResponse`, `RawEventDto`, `RawEventPage` stay in `adapter/in/web`.

The listener's commit policy is unchanged and stays in the adapter: unparseable payload → log + skip (commit); envelope with null `sourceId` or null `body` → log + skip; unknown or non-`active` source → log + skip; `IllegalArgumentException` from the use case → log + skip; any other `RuntimeException` propagates so the offset is not committed and the record is redelivered. What moves is only *where the unknown/inactive check is computed*: the listener stops injecting `EventSourceRepository` and asks `EventSourceStatePort` (or lets the use case throw `IllegalArgumentException`, which the listener already treats as permanent-skip). Observable behavior is identical either way.

**3. Outbound ports**

| Port | Methods |
|---|---|
| `RawEventStorePort` | `save(RawEvent) : UUID`, `findById(UUID) : Optional<RawEvent>`, `findPage(page, size) : RawEventBatch`, `count() : long` |
| `EventSourceStatePort` | `find(UUID) : Optional<SourceIngestState>`, `markSuccess(UUID, String adapterVersion, Instant)` |
| `ProcessRawEventBatchPort` | `process(List<UUID> rawEventIds)` |

**4. Outbound adapter implementations**: `adapter/out/persistence/RawEventPersistenceAdapter` over `RawEventJpaRepository` + `RawEventJpaMapper` (`RawEventJpaEntity` → `raw_events`; the `ObjectMapper` serialization of `attributes` / `rawPayload` into jsonb moves into the mapper); `adapter/out/persistence/EventSourceStateAdapter` delegating to the existing `configuration.persistence.EventSourceRepository` (the `configuration` context is untouched); `adapter/out/processing/ProcessRawEventBatchAdapter` calling `processing`'s inbound port `ProcessRawEventBatchUseCase` — this is what removes the direct `import ru.wisla.fm.processing.service.EventProcessingService` from `ingestion`.

The `accepted` / `rejected` counting stays in the use case: the `try/catch` wraps the `RawEventStorePort.save` call, so mapper/serialization failures surface through the port and are counted as `rejected` exactly as today.

**5. Infrastructure wiring**: `ingestion/infrastructure/config` declares `IngestEventsService` and `RawEventQueryService` as beans.

**6. Spring-free use-case tests**: `IngestEventsServiceTest` — heartbeat branch (`(0, 0, [], true)`, updates `adapterVersion` and `lastSuccessAt`); event batch (`accepted` / `rejected` / `rawEventIds`, one shared `ingestBatchId`); unknown source → `IllegalArgumentException`; `ProcessRawEventBatchPort` invoked only when `rawEventIds` is non-empty; default `status = "new"`; a save failure yields `rejected = 1` with the rest accepted. `RawEventQueryServiceTest` — paging and `createdAt desc`.

#### backend/fm-module — context `ru.wisla.fm.processing`

**1. Use case and inbound port**: `ProcessRawEventBatchUseCase.process(List<UUID> rawEventIds)`, implemented by `application/service/ProcessRawEventBatchService`, reproducing today's `EventProcessingService.processBatch` / `processRawEvent` algorithm step for step — skip `processed = true`, resolve CI, build the event, resolve the decision, dedup-or-save, apply threshold/correlation/notify/push intents, `markRun` executed rules, `markProcessed`, and on exception record `processing_error` without failing the batch.

**2. Inbound adapters**: none new — `ingestion`'s `ProcessRawEventBatchAdapter` is the only caller. The console REST surface (`api/EventController`, `EventQueryService`, `service/EventActionService`, `EventUpdateService`) stays where it is and is out of scope (D7).

**3. Outbound ports**

| Port | Methods |
|---|---|
| `RawEventStatePort` | `findById(UUID) : Optional<IncomingRawEvent>`, `markProcessed(UUID rawEventId, UUID eventId, UUID ciId)`, `recordError(UUID rawEventId, String message)` |
| `EventStorePort` | `save(Event) : Event`, `findById(UUID)`, `findActiveDuplicate(DedupKey) : Optional<Event>`, `countRecentBySeverity(...)`, `existsRecentByTitle(...)`, `findWindow(...)` |
| `CiLookupPort` | `findOrCreateByFqdn(String) : Optional<CiSnapshot>` |
| `RuleDefinitionPort` | `findEnabledRules() : List<RuleDefinition>`, `markRun(Set<UUID>, Instant)` |
| `NotificationPort` | `notify(UUID ruleId, String channel, String emailAddress)` |
| `PushNotificationPort` | `createPush(UUID ruleId, UUID eventId, String title, String message)` |

**4. Outbound adapter implementations**: `adapter/out/persistence/EventPersistenceAdapter` (`EventJpaEntity` → `events`, `EventActionLogJpaEntity` → `event_action_logs`, `EventJpaRepository`, `EventActionLogJpaRepository`, `EventJpaMapper`) containing the verbatim `CiId` / `CiIdIsNull` branching of D4; `adapter/out/ingestion/RawEventStateAdapter` delegating to `RawEventJpaRepository` (the single JPA mapping of `raw_events` stays owned by `ingestion`); `adapter/out/cmdb/CiLookupAdapter` → `cmdb.service.CiService`; `adapter/out/rules/RuleDefinitionAdapter` → `rules.persistence.ProcessingRuleRepository`, which also absorbs the canvas-JSON `ObjectMapper` parsing and the plan cache (key `ruleId`, invalidated by comparing `updatedAt`) currently inside `RuleCanvasEngine`, plus `last_run_at` updates; `adapter/out/notification/NotifyAdapter` → `notifications.api.NotifyStubService`; `adapter/out/notification/PushNotificationAdapter` → `notifications.api.PushNotificationService`.

Domain: `Event` (`fromRawEvent`, `synthetic`, `registerRepeat(now)`, `escalateSeverity(candidate)`, `assignRoot(id)`), `IncomingRawEvent` (a processing-owned read model, so `processing/domain` does not import `ingestion.domain`), `SeverityRank` (`fatal 0 < critical 1 < major 2 < minor 3 < warning 4 < other 5`, lifted from `DedupService.severityRank`), `DedupPolicy` / `DedupKey`, `ThresholdPolicy`, `CorrelationPolicy`, `ProcessingDecision` with `ThresholdIntent` / `CorrelationIntent` / `NotifyIntent` / `PushIntent` (already pure records), `CompiledRulePlan`, `RuleGraph` / `RuleNode` / `RuleEdge`, `RuleDefinition`. Domain services: `RuleGraphTraverser` (the BFS from `RuleCanvasEngine.traverseRule`, including `return` on a false `condition` node and the recursive `switch` branch), `RuleCanvasCompiler`, `RuleConditionEvaluator`, `SwitchBranchSelector`, `EventFactory`, `DedupMerger`, `ThresholdEvaluator` (synthetic title `"Threshold: N+ critical events in M minutes"`, `severity = fatal`, `attributes = {"synthetic":true,"ruleType":"threshold"}`), `CorrelationEvaluator`, `PushMessageRenderer` (`{title}` / `{severity}` substitution, default `"Событие"`).

**5. Infrastructure wiring**: `processing/infrastructure/config` declares `ProcessRawEventBatchService` and the domain services as beans; `@Transactional` for the processing path continues to come from the caller's transaction (the ingest inbound adapter), matching today's `REQUIRED` propagation.

**6. Spring-free use-case tests**: `ProcessRawEventBatchServiceTest` against fakes of all six ports — event creation from raw, CI binding with `systemName` / `subsystemName`, dedup branch vs direct save, threshold intent, correlation intent setting `rootEventId`, notify and push intents, `markRun` of executed rules, `markProcessed`, skipping `processed = true`, and an exception inside one raw event producing `recordError` without aborting the batch. Plus `RuleGraphTraverserTest`, `RuleCanvasCompilerTest`, `RuleConditionEvaluatorTest`, `SwitchBranchSelectorTest`, `DedupMergerTest`, `ThresholdEvaluatorTest`, `CorrelationEvaluatorTest`, `EventFactoryTest`, `PushMessageRendererTest`.

#### Integration points (unchanged)

| Integration | Mechanism | Where it lives after the refactor |
|---|---|---|
| adapter → fm-module ingest (data plane) | Kafka `fm.raw-events`, key `sourceId`, value `RawEventEnvelope{schemaVersion=1}` | producer: `adapter/out/kafka/RawEventKafkaPublisher`; consumer: fm-module `ingestion/adapter/in/messaging/RawEventKafkaListener`. Two private envelope copies, no shared type. |
| adapter → fm-module config (control plane) | HTTP `GET /api/v1/internal/sources` with `X-Service-Key` | `FmModuleSourceConfigPort` → `adapter/out/http/FmModuleSourceConfigClient` |
| adapter → fm-module probe/health | HTTP via existing `FmModuleClient` | `adapter/out/http` |
| SPA → fm-module REST | `GET|PATCH /api/v1/events...`, `POST /api/v1/events/{id}/actions`, dashboard, admin, rules | untouched — console controllers stay in place |
| `ingestion` → `processing` (in-process) | inbound port `ProcessRawEventBatchUseCase` behind `ProcessRawEventBatchPort` | replaces the direct `EventProcessingService` import |

### ArchUnit rule set

Both modules get `com.tngtech.archunit:archunit-junit5` at `test` scope and a `HexagonalArchitectureTest` (`backend/adapter/src/test/java/com/wisla/fm/adapter/architecture/`, `backend/fm-module/src/test/java/ru/wisla/fm/architecture/`). In fm-module every rule is prefixed with `.that().resideInAnyPackage("ru.wisla.fm.ingestion..", "ru.wisla.fm.processing..")`.

1. `..domain..` does not depend on `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `com.fasterxml.jackson..`, `org.apache.kafka..`, `org.springframework.kafka..`, `jakarta.servlet..`.
2. `..application..` does not depend on those packages, nor on `..adapter..` or `..infrastructure..`.
3. `..domain..` does not depend on `..application..` or `..adapter..`.
4. Classes annotated `@Entity` or `@Table` reside only in `..adapter.out.persistence..`.
5. Interfaces assignable to `org.springframework.data.repository.Repository` reside only in `..adapter.out.persistence..`.
6. Classes annotated `@RestController`, `@KafkaListener` or `@Scheduled` reside only in `..adapter.in..`.
7. `layeredArchitecture()` per context: `domain ← application ← adapter`; `infrastructure` may depend on all.
8. **Service independence (D0)**: in `backend/adapter`, no class in `com.wisla.fm.adapter..` depends on `ru.wisla.fm..`; in `backend/fm-module`, no class in `ru.wisla.fm..` depends on `com.wisla.fm.adapter..`.

## Risks / Trade-offs

- [**Someone "helpfully" extracts a shared contracts module** to remove the duplicated `RawEventEnvelope` / `IngestRequest`, coupling the two services' build and release cycles] → D0 states the prohibition; ArchUnit rule 8 fails the build on any cross-service import; the pom diff is asserted to contain only `archunit-junit5`; reviewers are pointed at D0 and at ADR-001's existing "no shared domain JAR" non-goal. The two copies are intentionally *not* structurally identical, so there is nothing to deduplicate.
- [**`@Transactional` moving from `IngestService` to the inbound adapters** changes when the transaction starts/commits, or changes failure behavior including Kafka offset commit] → D3 pins the mechanism; regression via `IngestControllerTest`, `RawEventKafkaConsumerTest`, `RawEventKafkaListenerTest`; a **new** test asserts that a processing failure records `raw_events.processing_error` and the surrounding transaction still commits the raw events; `propagation` / `isolation` / `readOnly` are not touched; AOP declaration in `infrastructure/config` is the documented fallback.
- [**`EventRepository` derived dedup queries** — the `...CiIdIsNull...` variant is used deliberately when `useCi = false`, and looks like a bug someone will "fix"] → D4; characterization tests for all four `useSource`/`useTitle`/`useCi` combinations against `ciId = null` and `ciId != null` are written **first**, before any code moves; the same treatment for the 4 threshold queries and the 6 correlation window queries; the branch is ported literally including the redundant-looking `if (ciId == null && config.useCi())`.
- [**ArchUnit vs `java.version = 25`** — ASM may refuse class file major version 69] → D5 makes this the very first task: add the dependency, run one trivial rule, and only then touch code. Escalation path if incompatible: newest ArchUnit release; failing that, ArchUnit moves to a follow-up change and this change is verified by review. **`java.version` of production code is never downgraded**, and Phase 2 is not blocked.
- [**`@JdbcTypeCode(SqlTypes.JSON)` columns** — `raw_events.payload` / `raw_payload` and `events.tags` / `attributes` are `String`; `source_config_snapshots.filter_rules` and `buffered_messages.payload` are `Map<String, Object>`; a `String ↔ Map` slip breaks jsonb writes and H2 compatibility] → D2 keeps field types, `columnDefinition` and `@JdbcTypeCode` byte-for-byte on the `*JpaEntity`; conversion happens only in the hand-written mapper; `Event.attributes` stays a JSON `String` so nothing is re-serialized; regression via the existing H2 integration tests and `RuleCanvasRuntimeIntegrationTest`.
- [**Blast radius of `EventEntity` → `EventJpaEntity`** — 8+ out-of-scope consumers: `DashboardService`, `AdminService`, `SourceService`, `ProductHealthService`, `DevDataSeeder`, `EventQueryService`, `EventActionService`, `EventUpdateService`] → D7: a separate "rename only" step with no logic change; those classes keep using the JPA repository directly (recorded deviation); regression via `DashboardControllerTest`, `AdminControllerTest`, `SourceControllerTest`, `ProductHealthControllerTest`, `EventControllerTest`.
- [`IngestPayloadMapper` holds non-trivial normalization (zabbix severity switch, `event_value=0`, field-priority fallbacks, `occurredAt` parsing) and is easy to lose a branch from while moving] → characterization `IngestPayloadNormalizerTest` covering the whole severity switch and every fallback chain is written before the move; `WebhookControllerTest` is the end-to-end guard.
- [`RuleCanvasEngine`'s unsynchronized `HashMap` plan cache changes invalidation semantics when it moves into `RuleDefinitionAdapter`] → move it with identical semantics (key `ruleId`, compare `updatedAt`); thread-safety is explicitly a follow-up, not this change; regression via `RuleCanvasRuntimeIntegrationTest` (rule update → recompile).
- [`identity`'s `UserEntity` eager `roles` fetch could produce `LazyInitializationException` or extra joins if transaction boundaries shift] → `identity` is not touched at all, console services keep reading `UserRepository` directly, and the processing engine introduces no port to `identity`; regression via `AuthControllerTest`, `EventControllerTest`, `AdminControllerTest`.
- [**Volume** — two phases plus ArchUnit plus a full JPA split in one change risks a half-done, red build] → strict step ordering with a mandatory green `mvn test` after each step; Phase 2 does not start until Phase 1 and ArchUnit are green; if the budget overruns, escalate at the gate and propose splitting Phase 2 into its own change.
- [ADR-001's own non-goals ("no ArchUnit", "no moving existing packages") now conflict with this pilot] → the last task amends ADR-001 to record the pilot and supersede those two non-goals; the ADR stays the single normative source rather than being silently contradicted.

## Migration Plan

No deployment, database migration, or runtime rollout step exists — this change alters only Java package structure and adds a test-scope dependency. Rollback is `git revert` of the relevant commits.

Ordering (each step ends with a green `mvn test` in the affected module):

1. **Compatibility and characterization gate.** Add `archunit-junit5` to both poms; run one trivial rule to prove ArchUnit can read Java 25 bytecode (escalate per D5 if not). Write characterization tests for the dedup / threshold / correlation query branching and for `IngestPayloadMapper`'s normalization **against the current code**, so the baseline is pinned before anything moves.
2. **`backend/adapter`.** Domain models and ports first (with their Spring-free tests), then use-case services, then inbound adapters, then delete the old `service/*` classes; JPA entities renamed and moved last.
3. **`backend/fm-module` `ingestion`.** Same inside-out order; ends with `ingestion` no longer importing `processing`.
4. **ArchUnit rules.** Add rules 1–8 in both modules and make them pass.
5. **`backend/fm-module` `processing`.** Domain services and ports first, then `ProcessRawEventBatchService`, then the six outbound adapters, then remove the two reverse dependencies (D6).
6. **Rename-only cleanup.** One commit updating the out-of-scope consumers to `EventJpaEntity` / `EventJpaRepository` with no logic change.
7. **Full verification.** `mvn test` in both modules; assert `git diff` is empty under `backend/*/src/main/resources/db/**`, `docs/**/api.yaml`, `frontend/**`, `prototype/**`, `backend/zabbix-simulator/**`, `backend/docker-compose*.yaml`, `backend/docker/**`; assert the pom diff adds only `archunit-junit5`.
8. **ADR-001 amendment** recording the pilot.

One git commit per coherent move, so each diff stays reviewable.

## Open Questions

- Should the ADR-001 amendment be an edit to ADR-001's Non-goals plus a "Pilot outcome" section, or a new ADR-002 that supersedes those two non-goals? (Recommendation: amend ADR-001, since it is the normative source and the pilot was its own stated next step.)
- If `@Transactional` on `RawEventKafkaListener.onMessage` interacts awkwardly with the container's `AckMode.RECORD`, do we switch to the AOP declaration in `infrastructure/config` (D3 fallback) or keep the transaction on a thin delegate inside `adapter/in/messaging`?
- `processing` keeps `IncomingRawEvent` as its own read model while `ingestion` owns the single `raw_events` JPA mapping, so `RawEventStateAdapter` in `processing` reaches into `ingestion`'s `RawEventJpaRepository`. Is that acceptable as an adapter-to-adapter dependency inside one service, or should `ingestion` expose a small port for it? (Recommendation: acceptable — both are `adapter/out` code inside the same deployable, and duplicating the `raw_events` mapping is the very hazard D1 warns about.)
- Should the plan-cache thread-safety follow-up and the `useCi = false` dedup-query oddity be filed as separate changes now, so they are not lost after this refactor?

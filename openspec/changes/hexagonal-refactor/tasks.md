## 1. Compatibility gate and characterization tests (before any code moves)

- [x] 1.1 Add `com.tngtech.archunit:archunit-junit5` with `<scope>test</scope>` to `backend/adapter/pom.xml` and `backend/fm-module/pom.xml`; change nothing else in either pom
- [x] 1.2 Add a throwaway ArchUnit smoke test in each module that imports the module's own package tree and asserts one trivial rule; run `cd backend/adapter; mvn test` and `cd backend/fm-module; mvn test` to prove ArchUnit can read `java.version = 25` bytecode (class file major version 69)
- [x] 1.3 If 1.2 fails on a bytecode-version error, retry with the newest ArchUnit release; if it still fails, remove the dependency and the smoke tests, record the finding, and **escalate at the gate** — ArchUnit moves to a follow-up change, `java.version` is NOT downgraded, and group 5 is skipped while everything else proceeds

  **Gate outcome: ArchUnit WORKS on Java 25 — no escalation, group 5 proceeds as planned.** `com.tngtech.archunit:archunit-junit5:1.4.2` (newest release; `maven-metadata.xml` `<latest>1.4.2</latest>`) imports class files of major version 69 in both modules without error and logs `Detected Java version 25.0.3`. Verified with `javap -v` that `target/classes` bytecode is `major version: 69`. Maven must run on JDK 25 (`JAVA_HOME=C:\java\jdk25`); the default `JAVA_HOME` on this workstation is JDK 17, which cannot compile `release 25` at all.
- [x] 1.4 Write characterization test `DedupQueryCharacterizationTest` against the **current** `DedupService` covering all four `useSource`/`useTitle`/`useCi` combinations against `ciId = null` and `ciId != null`, pinning that `useCi = false` still resolves to the `findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn` variant, and that all-keys-disabled creates a new event

  All 8 flag combinations × `ciId` null/non-null (16 rows) are pinned, not just four, plus the merge side (`repeat_count++`, `last_repeat_at`, severity escalation). **Second D4-class surprise found and pinned:** `useSource = false` / `useTitle = false` never widen the query — they only feed the "all keys null → `Optional.empty()`" guard, because both call sites fall back to `candidate.getSourceId()` / `candidate.getTitle()`. Three combinations therefore run no query at all and always create a new event: `(false,false,false)` with any `ciId`, and `(false,false,true)` with `ciId = null`.
- [x] 1.5 Write characterization tests against the current `ThresholdService` for its 4 count/exists queries (`CiId` vs `CiIdIsNull`), the synthetic title `"Threshold: N+ critical events in M minutes"`, `severity = fatal`, `attributes = {"synthetic":true,"ruleType":"threshold"}`, and title-idempotency within the window

  Also pinned: only the literal severity `"critical"` triggers evaluation (`"fatal"` does not), `criticalCount == count` already fires, the `exists` query reuses the *same* window start as the `count` query, and the boolean overload means `count = 5` / `windowMin = 10`.
- [x] 1.6 Write characterization tests against the current `CorrelationService` for its 6 window queries (`title`/`severity`/`source` × `CiId`/`CiIdIsNull`), first-event-as-root, adoption of an existing `rootEventId`, and absence of self-reference

  Also pinned: an unrecognised `matchField` falls back to the title window.
- [x] 1.7 Write characterization test `IngestPayloadMapperCharacterizationTest` against the current `IngestPayloadMapper` covering the whole `event_nseverity` 1..5 switch, `event_value = 0` → `status = closed`, `probe = true` → heartbeat body, the `externalId`/`title`/`occurredAt` field-priority chains, `occurredAt` parsing, and the severity fallback

  Also pinned: only the Boolean `true` triggers the heartbeat branch (the string `"true"` does not), `event_value = 0` overrides an explicit payload `status`, an unparseable `occurredAt` candidate falls through to the next field instead of aborting the chain, `attributes` and `rawPayload` are the same instance as the inbound payload, and the exact optional-field set of the produced event map.
- [x] 1.8 Write characterization test for the current `BufferedMessage.scheduleRetry` backoff, pinning `base * 2^min(retryCount - 1, 10)` including the cap at `2^10`
- [x] 1.9 Write characterization test for the current `SourceConfigSnapshot.replace(...)` upsert semantics, pinning that `created_at` is not overwritten on an existing snapshot
- [x] 1.10 Run `mvn test` in both modules — all characterization tests green against unmodified production code; commit this group on its own so the baseline is reviewable

  `backend/adapter`: 100 tests, 0 failures (baseline 33). `backend/fm-module`: 180 tests, 0 failures (baseline 112). `git diff` over `backend/` touches only the two `pom.xml` dependency blocks; every other change is a new test file. Commit intentionally left to the user.

## 2. backend/adapter — ingest context domain and ports

- [x] 2.1 Write `FilterRulesTest` (plain JUnit 5) for `enabled = false`, `drop_if`, `pass_only`, operators `eq`/`ne`/`contains`/`in`/`gt`/`lt`/`exists`, dotted-path lookup, and a missing field — red
- [x] 2.2 Create `com.wisla.fm.adapter.ingest.domain.FilterRules` and `FilterCondition` with the filter decision as domain behavior (no `@Service`); green
- [x] 2.3 Write `IngestPayloadNormalizerTest` mirroring the assertions of 1.7 but against the new API taking `adapterVersion` and `Clock` as parameters — red
- [x] 2.4 Create `domain/IngestPayloadNormalizer`; green
- [x] 2.5 Write `BufferedEventTest` mirroring 1.8 against `BufferedEvent.scheduleRetry(baseSeconds, now)` — red
- [x] 2.6 Create `domain/BufferedEvent`, `domain/SourceConfig` (with `isExpired(Clock)`), `domain/DeliveryOutcome`, `domain/IngestRejection` (carrying the frozen error code and HTTP status); green
- [x] 2.7 Declare inbound ports in `application/port/in`: `ReceiveWebhookEventUseCase`, `DeliverIngestEventUseCase`, `RetryBufferedEventsUseCase`, `SyncSourceConfigUseCase`, plus the `ReceiveWebhookCommand` and `DeliverCommand` records
- [x] 2.8 Declare outbound ports in `application/port/out`: `SourceConfigLookupPort`, `SourceConfigStorePort`, `BufferedEventStorePort`, `RawEventPublisherPort` (relocated `RawEventPublisher`, `PublishResult(success, error, retryable)` kept 1:1), `ApiKeyVerifierPort`, `FmModuleSourceConfigPort`
- [x] 2.9 Run `mvn test` in `backend/adapter` — green

  Verified by reading the code: `ingest/domain` and `ingest/application/port/**` contain no `org.springframework` / `jakarta.persistence` / Jackson / Kafka import, `SourceConfig.isExpired(Clock)` and `BufferedEvent.scheduleRetry(int, Instant)` are present, `RawEventPublisherPort.PublishResult` is declared 1:1. `backend/adapter`: 222 tests, 0 failures, exit 0.

## 3. backend/adapter — use-case services and adapters

- [x] 3.1 Write Spring-free `ReceiveWebhookEventServiceTest` with in-memory port fakes: 404 `unknown_source`, expired snapshot, 401 `invalid_source_key` on header/query mismatch, 401 `missing_api_key`, 401 `invalid_source_key` on wrong key, 403 `source_blocked`, 400 `filtered`, `forwarded`, `buffered` on retryable publish failure, 502 `ingest_rejected` on permanent failure — red
- [x] 3.2 Implement `application/service/ReceiveWebhookEventService` implementing `ReceiveWebhookEventUseCase` and `DeliverIngestEventUseCase`, depending only on domain types, `Clock` and ports; green
- [x] 3.3 Write Spring-free `RetryBufferedEventsServiceTest`: success deletes, permanent failure deletes, retryable reschedules via `scheduleRetry`, missing source config reschedules — red
- [x] 3.4 Implement `application/service/RetryBufferedEventsService`; green
- [x] 3.5 Write Spring-free `SyncSourceConfigServiceTest`: upsert, `blocked = status != "active"`, TTL `now + 86400s`, port failure does not throw — red
- [x] 3.6 Implement `application/service/SyncSourceConfigService`; green
- [x] 3.7 Create `adapter/out/persistence`: `SourceConfigSnapshotJpaEntity` and `BufferedMessageJpaEntity` (moved from `persistence/entity`, table/column names, `columnDefinition` and `@JdbcTypeCode(SqlTypes.JSON)` byte-for-byte identical), `SourceConfigSnapshotJpaRepository`, `BufferedMessageJpaRepository`, `SourceConfigJpaMapper`, `BufferedEventJpaMapper`, `SourceConfigPersistenceAdapter`, `BufferedEventPersistenceAdapter` — no MapStruct
- [x] 3.8 Move `kafka/*` to `adapter/out/kafka` (`RawEventKafkaPublisher`, the adapter's own `RawEventEnvelope` copy with `Map<String, Object> body`, `RawEventEnvelopeCodec`) implementing `RawEventPublisherPort`
- [x] 3.9 Create `adapter/out/crypto/PasswordEncoderApiKeyVerifier` and move `FmModuleClient` plus the new `FmModuleSourceConfigClient` (`RestClient`, `GET /api/v1/internal/sources`, `X-Service-Key`) into `adapter/out/http`, mapping the response into the adapter's own `SourceConfig` — no `ru.wisla.fm` import
- [x] 3.10 Move the web layer to `adapter/in/web`: `WebhookController` + new `WebhookPayloadReader` (owns the `max-payload-bytes` → 413 `payload_too_large` check and JSON parsing → 400 `invalid_json`), `InternalController`, `HealthController`, `GlobalExceptionHandler`, `dto/*` unchanged, plus the `SourceConfigSnapshotDto` mapping moved out of `SourceConfigService`

  The `SourceConfigSnapshotDto` mapping plus the "found but expired counts as absent" lookup landed in a new `adapter/in/web/SourceConfigSnapshotReader`, shared by `InternalController` and `ProbeService`; both used to call `SourceConfigService` for it.
- [x] 3.11 Create `adapter/in/scheduler/BufferRetryScheduler` (`@Scheduled(fixedDelayString = "${wisla.adapter.buffer-retry-interval-ms:60000}")` + `@Transactional`) and `adapter/in/scheduler/SourceConfigSyncScheduler` (`ApplicationRunner` + `@Scheduled(fixedDelayString = "${wisla.adapter.config-sync-interval-ms:300000}")`)
- [x] 3.12 Move `ProbeService` and `HealthService` onto `DeliverIngestEventUseCase` and the ports with minimal mechanical edits, keeping `ProbeResponse` (`latency`, `delivery`, `ingest_status`) and `HealthResponse` unchanged

  Both records are byte-identical apart from their package line. The probe's own `source_blocked` rejection keeps its distinct `422` / `"Source is blocked"` shape — it is deliberately *not* the webhook's `403` / `"Source is blocked due to event storm"`; verified against `git show HEAD:..service/ProbeService.java`.
- [x] 3.13 Move `config/*` to `infrastructure/config` (`AppConfig`, `WebClientConfig`, `AdapterProperties`, `KafkaIngestProperties`), keeping the `wisla.adapter.*` and `wisla.kafka.*` prefixes, and declare the use-case services as `@Bean`s wired with ports and a `Clock`
- [x] 3.14 Delete `service/WebhookService`, `FilterService`, `IngestPayloadMapper`, `BufferService`, `BufferRetryWorker`, `SourceConfigService`, `SourceConfigSyncService`, and the old `persistence/` and `kafka/` packages; update `BufferRetryWorkerTest`, `RawEventKafkaPublisherTest`, `RawEventEnvelopeCodecTest`, `WebhookControllerTest`, `InternalControllerTest`, `HealthControllerTest` and the `testsupport/*` classes by imports and type names only

  All of `service/`, `web/`, `config/`, `kafka/` and `persistence/` are gone; `main` is now `AdapterApplication` plus `ingest/**` only. The six named tests are pure renames — their method sets were diffed against `HEAD` and are identical (`WebhookControllerTest` 11 `@Test`, `InternalControllerTest` 12, `HealthControllerTest` 1, `RawEventKafkaPublisherTest` 3, `RawEventEnvelopeCodecTest` 3, `BufferRetryWorkerTest` → `BufferRetrySchedulerTest` 3). `testsupport/SourceConfigTestData` now builds `SourceConfigSnapshotJpaEntity`; the 8-arg `BufferedMessageJpaEntity` constructor made the old inline 4-arg construction impossible, so `testsupport/BufferedMessageTestData` was added to keep `BufferRetrySchedulerTest` byte-identical in intent.
- [x] 3.15 Run `cd backend/adapter; mvn test` — green, with no existing test removed, disabled, or weakened

  `backend/adapter`: **166 tests, 0 failures, exit 0**. `backend/fm-module` re-run unchanged: **199 tests, 0 failures, exit 0**.

  **Hazard from the partial 3.7 is resolved.** The `@Entity` inventory under `backend/adapter/src` is now exactly `BufferedMessageJpaEntity` → `buffered_messages` and `SourceConfigSnapshotJpaEntity` → `source_config_snapshots`, both in `ingest/adapter/out/persistence`. One mapping per table, as D1 requires. No `ru.wisla.fm` string anywhere under `backend/adapter` (sources or pom), and the pom still declares no fm-module dependency.

  **Test-count delta 222 → 166 is one file, and it is the 9.8 case.** The temporary group-1 characterization test `IngestPayloadMapperCharacterizationTest` (56 cases) was removed: its subject `service/IngestPayloadMapper` is deleted by 3.14, so it cannot compile, and task 2.3 built `IngestPayloadNormalizerTest` (57 cases) as a mirror of exactly those assertions plus one extra. That is precisely the removal 9.8 authorises, done early because 3.14 forces it. Every other test survives. The two other group-1 characterization tests were **reworked rather than removed**, keeping all 5 + 5 assertions: `BufferedMessageBackoffCharacterizationTest` now pins the same `base * 2^min(retryCount - 1, 10)` curve as it lands in the row via `BufferedEvent.scheduleRetry` + `BufferedEventJpaMapper.toEntity`, and `SourceConfigSnapshotUpsertCharacterizationTest` pins the same `created_at`-survives-replace semantics on `SourceConfigSnapshotJpaEntity`.

  **Left for group 5:** `adapter/out/kafka/RawEventKafkaPublisher` reads `KafkaIngestProperties` from `ingest/infrastructure/config`, i.e. an `adapter` → `infrastructure` dependency. The 5.1 `layeredArchitecture()` rule wants `infrastructure` to be a free layer, so that rule will flag this class unless the properties record moves next to its consumer or the layer definition permits it. Decide it in 5.1; nothing in group 3 depends on the outcome.

## 4. backend/fm-module — ingestion context

- [x] 4.1 Write Spring-free `IngestEventsServiceTest` with fakes of `RawEventStorePort`, `EventSourceStatePort` and `ProcessRawEventBatchPort`: heartbeat branch returns `(0, 0, [], true)` and updates `adapterVersion`/`lastSuccessAt`; event batch reports `accepted`/`rejected`/`rawEventIds` with one shared `ingestBatchId`; a save failure yields `rejected = 1` with the rest accepted; unknown source throws `IllegalArgumentException`; `ProcessRawEventBatchPort` invoked only for a non-empty id list; default `status = "new"` — red
- [x] 4.2 Create `ingestion/domain`: `RawEvent`, `RawEventBatch`, `IngestOutcome`, `SourceIngestState` (jsonb-backed fields keep type `String`)
- [x] 4.3 Declare `application/port/in/IngestEventsUseCase` (with `IngestCommand(sourceId, heartbeat, events, adapterVersion, receivedAt)`) and `QueryRawEventsUseCase`; declare `application/port/out/RawEventStorePort`, `EventSourceStatePort`, `ProcessRawEventBatchPort`
- [x] 4.4 Implement `application/service/IngestEventsService` with the `try/catch` around `RawEventStorePort.save` preserved so `accepted`/`rejected` counting is unchanged; green for 4.1
- [x] 4.5 Write Spring-free `RawEventQueryServiceTest` (paging, `createdAt desc`) — red; implement `application/service/RawEventQueryService`; green

  `QueryRawEventsUseCase` is `query(sourceId, severity, processed, page, size)`, not the `query(page, size)` of `design.md` — the design under-specified it; the old `RawEventQueryService.listRawEvents` already took the three filters and `GET /api/v1/raw-events` still exposes them, so the REST contract is unchanged. `createdAt desc` and the `size` clamp to `[1, 500]` live in `RawEventPersistenceAdapter` / `RawEventQueryService` respectively, matching the old service.
- [x] 4.6 Create `adapter/out/persistence`: `RawEventJpaEntity` (moved from `ingestion/domain/RawEventEntity`, table `raw_events` and the `payload`/`raw_payload` jsonb mappings byte-for-byte identical), `RawEventJpaRepository`, `RawEventJpaMapper` (owns the Jackson serialization), `RawEventPersistenceAdapter`, and `EventSourceStateAdapter` delegating to the existing `configuration.persistence.EventSourceRepository`
- [x] 4.7 Create `adapter/out/processing/ProcessRawEventBatchAdapter` calling `processing`'s `EventProcessingService` for now (it becomes `ProcessRawEventBatchUseCase` in group 6), so `ingestion` no longer imports `processing` outside this one adapter class
- [x] 4.8 Move `IngestController` to `adapter/in/web` with `@Transactional` on `ingest`, extracting the `UUID` principal from the `SourceApiKeyAuthenticationFilter` authentication and building an `IngestCommand`; move `RawEventController`, `IngestRequest`, `IngestResponse`, `RawEventDto`, `RawEventPage` to `adapter/in/web` unchanged
- [x] 4.9 Move `RawEventKafkaListener` and fm-module's own `RawEventEnvelope` copy (`IngestRequest body`, `@Valid`/`@NotNull`/`@NotBlank`, `JsonInclude.NON_NULL`) to `adapter/in/messaging`; add `@Transactional` to `onMessage`; drop the `EventSourceRepository` injection so the unknown/inactive check comes from `EventSourceStatePort` or from `IllegalArgumentException`; keep logging and the skip-commit/propagate policy identical

  Verified line by line against `git show HEAD:...ingestion/kafka/RawEventKafkaListener.java`: the four skip branches, their log levels and message strings, and the `IllegalArgumentException`-is-permanent / other-`RuntimeException`-propagates split are byte-identical. `!"active".equalsIgnoreCase(source.getStatus())` became `!SourceIngestState.isActive()`, which is the same comparison.
- [x] 4.10 Create `ingestion/infrastructure/config` declaring `IngestEventsService` and `RawEventQueryService` as beans; delete `ingestion/api/IngestService`, `ingestion/api/RawEventQueryService`, `ingestion/persistence`, `ingestion/kafka`
- [x] 4.11 Write a new test asserting that a processing failure inside the ingest transaction records `raw_events.processing_error` and the transaction still commits the raw events — this is the explicit regression for the `@Transactional` move

  Did not exist; written as `ingestion/adapter/in/web/IngestTransactionBoundaryTest`. It posts a two-event batch to `POST /api/v1/ingest`, fails processing of the first event only, and then asserts from outside the request that both `raw_events` rows are committed, that the failed row carries `processing_error` with `processed = false` and no `processed_event_id`, and that the second row was processed normally.

  The failure is injected by a `@TestConfiguration` `@Primary` `RuleCanvasEngine` subclass that throws from `resolveActions`. That method is deliberately chosen because it is **not** `@Transactional`: a failure raised inside a nested `@Transactional` collaborator (`CiService`, `DedupService`, any Spring Data `save`) would mark the shared transaction rollback-only and the commit would fail with `UnexpectedRollbackException` instead of committing. That is pre-existing behavior, unchanged by this refactor, but it means the swallow-and-record path in `EventProcessingService` only really survives in-memory failures.
- [x] 4.12 Update `IngestServiceTest`, `IngestControllerTest`, `RawEventKafkaConsumerTest`, `RawEventKafkaListenerTest`, `RawEventEnvelopeTest` by imports and type names only

  `IngestControllerTest`, `RawEventEnvelopeTest` and `RawEventKafkaConsumerTest` were imports/type-names/package only. Two needed more than a rename:

  - `IngestServiceTest` → `application/service/IngestEventsServiceIntegrationTest`. Its subject `IngestService` no longer exists; it now drives `IngestEventsUseCase` with `IngestRequest.toCommand(sourceId)`, which keeps the original input shape and additionally covers the DTO→command mapping. Both test methods and every assertion are preserved verbatim.
  - `RawEventKafkaListenerTest` → `adapter/in/messaging/RawEventKafkaListenerTest`, genuinely reworked because 4.9 dropped the `EventSourceRepository` injection: the `EventSourceRepository` JDK proxy became an `EventSourceStatePort` fake and the `IngestService` subclass became an `IngestEventsUseCase` lambda recording the `IngestCommand`. All four original behaviors are kept; three cases were **added** for the policy branches that the moved collaborator now decides — inactive source, null `sourceId`/`body` envelope, and `IllegalArgumentException` from the use case (the new permanent-skip signal for an unknown source).
- [x] 4.13 Run `cd backend/fm-module; mvn test` — green

  199 tests, 0 failures, exit 0 (baseline 180 + 19: `IngestEventsServiceTest` 10, `RawEventQueryServiceTest` 5, `IngestTransactionBoundaryTest` 1, `RawEventKafkaListenerTest` +3). No test was removed, disabled or weakened.

## 5. ArchUnit rules (skip only if 1.3 escalated)

- [x] 5.1 Replace the smoke test in `backend/adapter/src/test/java/com/wisla/fm/adapter/architecture/HexagonalArchitectureTest.java` with rules 1–7 scoped to `com.wisla.fm.adapter.ingest..`: domain free of `org.springframework..`/`jakarta.persistence..`/`org.hibernate..`/`com.fasterxml.jackson..`/`org.apache.kafka..`/`org.springframework.kafka..`/`jakarta.servlet..`; application free of the same and of `..adapter..`/`..infrastructure..`; domain free of `..application..`/`..adapter..`; `@Entity`/`@Table` only in `..adapter.out.persistence..`; Spring Data `Repository` only in `..adapter.out.persistence..`; `@RestController`/`@KafkaListener`/`@Scheduled` only in `..adapter.in..`; `layeredArchitecture()` `domain ← application ← adapter` with `infrastructure` free

  8 rules (1–7 plus the method-level half of rule 6), all green. Three formulation details were forced by this module:

  **Relative package patterns are unusable here.** This module's root package is `com.wisla.fm.adapter`, so the pattern `..adapter..` matches *every* class in it, `ingest.domain` included. Written relatively, rule 2 would have read "application must not depend on anything" and rule 3 "domain must not depend on itself". All patterns are therefore spelled out from `com.wisla.fm.adapter.ingest`.

  **Rule 6 needs a method-level rule.** `@Scheduled` is a method annotation and `@KafkaListener` may be either, so a class-level `areAnnotatedWith` rule for them is empty and ArchUnit 1.x fails an empty `should` by default. Rules 4–6 are written as `noClasses().that(<in scope>).and().resideOutsideOfPackage(<allowed>).should().beAnnotatedWith(...)` plus a `noMethods()` twin for `@Scheduled`/`@KafkaListener`; that form is logically identical, always has a non-empty subject set, and needs no `allowEmptyShould`.

  **`KafkaIngestProperties` decision (the hazard left by 3.15): Option B — the layer definition permits `adapter → infrastructure`, and no production code changed.** Probed by temporarily tightening the rule to `whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()`: **15 violations, not 1** — `RawEventKafkaPublisher` reads `KafkaIngestProperties` (3), and `AdapterProperties` is read by `adapter/in/web/HealthService` (4), `adapter/in/web/InternalController` (3), `adapter/in/web/WebhookPayloadReader` (2) and `adapter/out/http/FmModuleSourceConfigClient` (3). Option A (move the record next to its consumer) only ever addressed the Kafka case; `AdapterProperties` has four consumers in two different adapter packages, so moving it would just relocate the same edge, and it would change the `@ConfigurationProperties` bean name for zero benefit. The rule is `whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapter")` — **stricter than the spec**, which only requires that infrastructure may depend on all layers and says nothing about access to it. `domain` and `application` stay cut off from `infrastructure/config`: the layer rule forbids it and rule 2 asserts it directly. Behavior is provably identical because no production file was touched.
- [x] 5.2 Add **rule 8 (service independence)** to the adapter test: no class residing in `com.wisla.fm.adapter..` may depend on classes residing in `ru.wisla.fm..`

  `adapterServiceDoesNotDependOnFmModule`, module-wide rather than scoped to the `ingest` context.
- [x] 5.3 Create `backend/fm-module/src/test/java/ru/wisla/fm/architecture/HexagonalArchitectureTest.java` with rules 1–7, every rule scoped with `resideInAnyPackage("ru.wisla.fm.ingestion..", "ru.wisla.fm.processing..")` so the ten unmigrated contexts cannot fail the build; no `@ArchIgnore` or suppression list

  Created, structurally parallel to the adapter test, single `IN_SCOPE` constant shared by every rule, no `@ArchIgnore` and no suppression list. **Deviation: `IN_SCOPE` is `{"ru.wisla.fm.ingestion.."}` only — `"ru.wisla.fm.processing.."` is not in it yet.** Measured, not assumed: with both packages in scope the run is **10 tests, 4 failures, 52 violations, and every single violation is in the unmigrated `processing` context** — rule 1 ×45 and rule 4 ×4 from `processing/domain/EventEntity` + `EventActionLogEntity`, rule 5 ×2 from `processing/persistence/EventRepository` + `EventActionLogRepository`, rule 6 ×1 from `processing/api/EventController`. Nothing in `ingestion` violates anything. The first three groups of violations are exactly what groups 6–7 delete or move, so scoping the rules to `processing` **before** that migration would only assert that the migration has not happened — group 5 sits before group 6 in this plan, while the spec's rule set assumes both contexts are already migrated (its own wording, "the `@Entity`/repository rules are satisfied *once the JPA classes have moved*", concedes the ordering).

  **The rule-6 violation is not an ordering problem and needs a decision at 7.10/8.2:** `processing/api/EventController` is a `@RestController` that D7 and 8.2 deliberately leave in place, out of the hexagonal layout, for the whole change. So `ru.wisla.fm.processing..` can be added to `IN_SCOPE` for rules 1–5 and 7 once group 7 lands, but rule 6 cannot cover that context unless the console controller moves to `adapter/in` — which is out of scope. Both facts are recorded in the `IN_SCOPE` javadoc so the widening step cannot be lost.

  Also: only the `ingestion` `layeredArchitecture()` rule is kept. A `processing` one passes today, but only because three of its four layers do not exist — a rule that green-lights an unmigrated context is worse than no rule. It is added with the scope widening.

  `consideringOnlyDependenciesInLayers()` is what keeps the ten unmigrated contexts out of rule 7 without a suppression: `processing/api/EventQueryService`, `dashboard`, `admin`, `configuration`, `health` and `config/DevDataSeeder` reside in no layer, so their reads of this context's JPA types are not judged. Without it, `consideringAllDependencies()` would flag every one of them — and would still flag them after group 8, since D7 keeps them on `EventJpaRepository` permanently.
- [x] 5.4 Add **rule 8 (service independence)** to the fm-module test: no class residing in `ru.wisla.fm..` may depend on classes residing in `com.wisla.fm.adapter..`

  `fmModuleDoesNotDependOnTheAdapterService`, module-wide — the one rule in this file that is not scoped by `IN_SCOPE`.
- [x] 5.5 Verify rule 8 actually bites: temporarily introduce a cross-service import in each module, confirm the build fails, then revert

  A cross-service import cannot simply be written, because neither service is on the other's classpath — that is D0 working as intended. Each probe therefore needed two temporary `src/main/java` classes: a stub in the *other* service's package namespace plus a consumer in the module's own. Both probes failed the build, each with exactly one failing test and no collateral failures:

  - `backend/adapter` — `Rule 'no classes that reside in a package 'com.wisla.fm.adapter..' should depend on classes that reside in any package ['ru.wisla.fm..'], because the two services stay compile-time independent (D0)' was violated (1 times): Method <com.wisla.fm.adapter.ingest.TempViolation.probe()> calls method <ru.wisla.fm.TempProbe.name()> in (TempViolation.java:9)` — `Tests run: 9, Failures: 1`, `BUILD FAILURE`.
  - `backend/fm-module` — `Rule 'no classes that reside in a package 'ru.wisla.fm..' should depend on classes that reside in any package ['com.wisla.fm.adapter..'], because the two services stay compile-time independent (D0)' was violated (1 times): Method <ru.wisla.fm.TempViolation.probe()> calls method <com.wisla.fm.adapter.TempProbe.name()> in (TempViolation.java:9)` — `Tests run: 9, Failures: 1`, `BUILD FAILURE`.

  All four probe files reverted. **Trap worth knowing:** deleting the sources is not enough — `maven-compiler-plugin` does not remove orphaned `.class` files, so ArchUnit kept importing the probes from `target/classes` and rule 8 stayed red. The four stale class files were removed and both modules were then re-verified with `mvn clean test`.
- [x] 5.6 Run `mvn test` in both modules — green

  `mvn clean test` on `JAVA_HOME=C:\java\jdk25`, both **exit 0**:

  - `backend/adapter`: **174 tests, 0 failures, 0 errors** (166 at 3.15, minus the 1-test 1.2 smoke test that 5.1 replaced, plus 9 architecture rules).
  - `backend/fm-module`: **208 tests, 0 failures, 0 errors** (199 at 4.13 plus 9 architecture rules).

  Both `HexagonalArchitectureTest` classes report `Tests run: 9, Failures: 0`. fm-module's throwaway `ru/wisla/fm/architecture/ArchUnitBytecodeSmokeTest` is left in place for 9.8 to delete; the adapter's smoke test is already gone, replaced in its own file by 5.1. No existing test was removed, disabled or weakened.

## 6. backend/fm-module — processing domain and domain services

- [x] 6.1 Write `DedupMergerTest` mirroring the assertions pinned in 1.4: `repeat_count++`, `last_repeat_at`, severity escalation only upward using `fatal 0 < critical 1 < major 2 < minor 3 < warning 4 < other 5`, all keys disabled creates a new event — red

  34 cases, red first: `mvn -o test-compile` failed with `cannot find symbol: class DedupMerger` before any domain service existed. The merge assertions are a 1:1 mirror of 1.4 (same 9-row severity table, `repeat_count` 1 → 2, `last_repeat_at` now exact rather than a range because the clock is a parameter). **The "all keys disabled creates a new event" half could not live on `DedupMerger`**: the merger cannot see the lookup, so that behavior lives on `DedupKey.from(candidate, policy)` and is asserted through `lookupRequired()`. All 16 flag × `ciId` rows of 1.4 are carried over, mapped onto `lookupRequired` — the same three rows that ran no query in 1.4 are the three `false` rows here. Both D4 surprises are pinned again at this level: `useCi = false` yields `ciId = null` while keeping `useCi()` distinguishable (so the adapter can still reproduce the redundant-looking `ciId == null && useCi` branch), and `sourceId`/`title` are always the candidate's own values regardless of `useSource`/`useTitle`.
- [x] 6.2 Create `processing/domain`: `Event` (`fromRawEvent`, `synthetic`, `registerRepeat(now)`, `escalateSeverity(candidate)`, `assignRoot(id)`; `attributes` stays a JSON `String`), `IncomingRawEvent` (processing-owned read model, no `ingestion.domain` import), `SeverityRank`, `DedupPolicy`, `DedupKey`, `ThresholdPolicy`, `CorrelationPolicy`; move `ProcessingDecision` and its `ThresholdIntent`/`CorrelationIntent`/`NotifyIntent`/`PushIntent` records, `CompiledRulePlan`, and introduce `RuleGraph`/`RuleNode`/`RuleEdge`/`RuleDefinition`

  All 14 types created. Five decisions the task text left open:

  - **`Event` is a mutable class, not a record**, and its field set plus accessor names mirror `EventEntity` exactly (minus the annotations, plus `setId`/`setCreatedAt`/`setUpdatedAt` that `EventJpaMapper` needs in 7.4). Mutation is what keeps the correlation path behaviorally identical: today `CorrelationService` sets `root_event_id` on the very instance it then saves, and `DedupService` returns the same instance it merged into. The `status = "new"`, `repeat_count = 1`, `tags = "[]"`, `attributes = "{}"` field initializers are carried over too — the threshold characterization test asserts all four on the synthetic event.
  - **`escalateSeverity` takes a `String`, not an `Event`.** `DedupService` compares `candidate.getSeverity()` against `event.getSeverity()`, so a severity is the whole input.
  - **`SeverityRank.of` keeps switching on a bare `String`**, so a `null` severity still throws exactly as `DedupService.severityRank` does. Adding a null guard would have been a behavior change (NPE → no escalation).
  - **`CiSnapshot` had to be added.** It is listed in `design.md` only as the return type of `CiLookupPort` (task 7.1), but `EventFactory`/`Event.fromRawEvent` need it in this group. Created as `processing/domain/CiSnapshot(id, fqdn, systemName, subsystemName)`.
  - **`Event.ACTIVE_STATUSES`** is where the frozen `["new", "in_progress", "maintenance", "deferred"]` set now lives, so `DedupKey`, `ThresholdEvaluator` and `CorrelationEvaluator` share one definition. Nothing in production reads it yet; 7.5 does.

  `RuleNode`/`RuleEdge` are new records mirroring `CanvasNodeView`/`CanvasEdgeView` method for method, and `RuleGraph` mirrors `rules.api.RuleCanvasDto`. They are **introduced, not moved**: `CanvasNodeView`/`CanvasEdgeView` stay in `processing/canvas` because `rules.api.RuleCanvasValidator` still imports them until 7.9. Likewise `DedupPolicy`/`ThresholdPolicy`/`CorrelationPolicy` are new alongside `canvas/DedupConfig`/`ThresholdConfig`/`CorrelationConfig`, which the still-living `DedupService`/`ThresholdService`/`CorrelationService` and their group-1 characterization tests need until 7.10 — so no existing test had to move onto the new types. `ProcessingDecision` and `CompiledRulePlan` **were** moved (deleted from `canvas`), with `dedupConfig()` → `dedupPolicy()` and `CorrelationIntent.config()` → `CorrelationIntent.policy()`; the moved `ProcessingDecision` is also reformatted, because the original file had a stray blank line between every single line.
- [x] 6.3 Implement `domain/service/DedupMerger`; green for 6.1
- [x] 6.4 Write `ThresholdEvaluatorTest` and `CorrelationEvaluatorTest` mirroring 1.5 and 1.6 — red; implement `domain/service/ThresholdEvaluator` and `CorrelationEvaluator`; green

  15 and 13 cases. **Both evaluators own a nested `Window` interface** (`ThresholdEvaluator.Window`: `countRecentCritical` + `hasRecentSynthetic`; `CorrelationEvaluator.Window`: `findWindow` + `findById`), which 7.3 implements over `EventStorePort`. That is what keeps the query *ordering* in the domain rather than leaking it into the use case: 1.5 pins that the `exists` query only runs once the count has breached and reuses the *same* window start, and 1.6 pins that `findById` only runs when the window root is already rooted. Handing the evaluators pre-fetched numbers instead would have moved both facts into the caller.

  Everything else from 1.5 is mirrored: only the literal `"critical"` qualifies (`"fatal"` does not), `criticalCount == count` already fires, the frozen title/description/`severity = fatal`/`attributes`/`tags`/`repeatCount`, and topology inheritance from the trigger. Everything from 1.6 too: oldest-in-window becomes root, transitive root adoption, no self-reference, window-smaller-than-count does nothing, plus one case 1.6 did not cover — an unresolvable `root_event_id` degrades to the window root itself (`orElse(root)`).

  **The `matchField` → query switch stayed in the query layer, so the domain test cannot assert the six variants.** `CorrelationService.findWindowEvents` picks between 6 derived queries; D4 puts that in `EventPersistenceAdapter`, so `CorrelationEvaluator` hands `matchField` through untouched and the test pins that pass-through for `title`/`severity`/`source` *and* for unrecognised values. That an unrecognised value resolves to the title window stays asserted by `CorrelationServiceCharacterizationTest` and must be reproduced verbatim in 7.5.
- [x] 6.5 Write `EventFactoryTest` (raw → event field mapping, `status = "new"`, CI-derived `ciId`/`systemName`/`subsystemName`) and `PushMessageRendererTest` (`{title}`, `{severity}`, default event title, final default `"Событие"`) — red; implement `domain/service/EventFactory` and `PushMessageRenderer`; green

  5 and 7 cases. `EventFactory.create(raw, ci)` delegates to `Event.fromRawEvent`; both are required by name (6.2 and 6.5), so the service is a one-line seam. Pinned beyond the task text: the raw event's own `status` is *not* carried over (a new event is always `"new"`, even from a `closed` raw event), `attributes` is the raw `payload` string assigned unconditionally, and a missing CI leaves `ciId`/`systemName`/`subsystemName` null. `PushMessageRenderer` is `resolvePushMessage` verbatim, including that a null title or severity substitutes an empty string and that an unknown placeholder is left in the message untouched. Neither is wired into production in this group — `EventProcessingService` keeps its own inline construction and private `resolvePushMessage` until 7.3/7.10 replace it, which keeps this group's diff off the live event-creation path.
- [x] 6.6 Write `RuleGraphTraverserTest` covering trigger/condition/switch/dedup/threshold/correlation/notify/push nodes, traversal stopping on a false `condition`, the recursive `switch` branch, and the legacy fallback (`dedup` → default config, `threshold` → default config, `correlation` → `CorrelationConfig(2, 10, "title")`) — red

  21 cases. Four traversal properties that had no test before and are easy to "clean up" by accident are now pinned:

  - A false `condition` **returns from the whole traversal**, not just its own branch — a sibling action already queued next to it is dropped too.
  - A `switch` node **ends the outer traversal** after recursing into the selected branch, so anything queued alongside the switch is never visited.
  - An **unrecognised node type is a dead end**: it neither acts nor forwards, because only the eight known types call `enqueueTargets`.
  - The **legacy fallback does not call `markExecuted`**, so a canvas-less rule never updates its `last_run_at`. Only canvas action nodes do.

  Also covered: a canvas with nodes but no `trigger`/`triggerType=stream` node is skipped entirely (`triggerNodeId == null`), an unknown legacy `ruleType` contributes nothing, and with two dedup rules the first one wins the `dedupPolicy` while both still count as executed.
- [x] 6.7 Move `RuleCanvasCompiler`, `RuleConditionEvaluator`, `SwitchBranchSelector` into `domain/service` as plain classes (no Spring stereotypes) and implement `domain/service/RuleGraphTraverser` from `RuleCanvasEngine.traverseRule`/`resolveActions`/`applyLegacyFallback`; green for 6.6

  All three moved, `@Component` dropped, `final` added; `RuleCanvasCompiler.compile` now takes `RuleGraph` instead of `rules.api.RuleCanvasDto`, which is what removes the `processing → rules` import from the compiler.

  **Two things the task text did not mention but the move forces:**

  1. **`processing/infrastructure/config/ProcessingConfig` had to be created now.** The three classes lost their stereotype but `RuleCanvasEngine` still injects them, so they need `@Bean` declarations. It holds only those three; 7.10 adds `ProcessRawEventBatchService` and the remaining domain services.
  2. **`RuleCanvasEngine` survives this group and had to be rewired, not just recompiled.** 7.10 is what deletes it. It keeps canvas-JSON parsing, the plan cache and `updateLastRunAt` (7.7's job), and its `resolveActions` now delegates to `RuleGraphTraverser`. Its constructor signature is deliberately unchanged — it builds the traverser internally from the evaluator and selector it is given — so `RuleCanvasEngineTest` and `IngestTransactionBoundaryTest`'s `@Primary` subclass both keep working. Its `resolveActions(RawEventJpaEntity, EventEntity, …)` signature is also unchanged, with the two conversions to `IncomingRawEvent`/`Event` done privately inside it. That deliberately keeps the interim glue inside the class group 7 deletes and keeps `EventProcessingService` off the domain types, so the live event-creation path is untouched in this group.

  `EventProcessingService` therefore changed by imports plus two one-line policy→config conversions (`DedupPolicy` → `canvas/DedupConfig`, `CorrelationPolicy` → `canvas/CorrelationConfig`) for the three old `@Service`s it still calls. `notifications/api/NotifyStubService` changed by one import line (`processing.canvas.ProcessingDecision` → `processing.domain.ProcessingDecision`); its signature still takes `NotifyIntent` until 7.8.
- [x] 6.8 Update `RuleCanvasCompilerTest`, `RuleConditionEvaluatorTest`, `SwitchBranchSelectorTest`, `RuleCanvasEngineTest` onto the domain types by imports and type names only

  All four keep their exact method sets and assertions (3 / 4 / 2 / 4 tests). The first three moved to `ru/wisla/fm/processing/domain/service/` with their subjects; `RuleCanvasEngineTest` stays in `processing/canvas` because its subject does.

  **One deviation from "imports and type names only", forced by `IncomingRawEvent` being a record:** `RuleConditionEvaluatorTest` and `SwitchBranchSelectorTest` each had a case that mutated the raw event (`raw.setSeverity("warning")`). Records are immutable, so both now build a second raw event from a private `raw(severity)` helper instead. Assertions are byte-identical.

  Also updated, though not on the list, because `ProcessingDecision`/`CompiledRulePlan`/the three moved services changed package: `IngestTransactionBoundaryTest` — import lines only, its `@Primary RuleCanvasEngine` subclass and the overridden `resolveActions(RawEventJpaEntity, EventEntity, List<CompiledRulePlan>)` signature are unchanged.
- [x] 6.9 Run `cd backend/fm-module; mvn test` — green

  `mvn -o clean test` on `JAVA_HOME=C:\java\jdk25`: **303 tests, 0 failures, 0 errors, exit 0** (208 at 5.6 plus 95: `DedupMergerTest` 34, `RuleGraphTraverserTest` 21, `ThresholdEvaluatorTest` 15, `CorrelationEvaluatorTest` 13, `PushMessageRendererTest` 7, `EventFactoryTest` 5). No test was removed, disabled or weakened — the three moved test classes and `RuleCanvasEngineTest` still report 3 / 4 / 2 / 4, and all three group-1 processing characterization tests still run against the untouched `DedupService` / `ThresholdService` / `CorrelationService` (32 / 17 / 18 cases).

  `HexagonalArchitectureTest` is still 9 tests, 0 failures, and `IN_SCOPE` was **not** widened, as instructed. Verified by grep that nothing under `processing/domain/**` imports Spring, `jakarta.*`, Hibernate, Jackson or Kafka except the two pre-existing legacy JPA classes `EventEntity` / `EventActionLogEntity` that 7.4 moves to `adapter/out/persistence` — and that no new domain type imports `ingestion`, `rules`, `cmdb` or `notifications`, so rules 1–3 and 7 will hold for `ru.wisla.fm.processing..` once 7.10 lands.

## 7. backend/fm-module — processing use case, ports and adapters

- [x] 7.1 Declare `application/port/in/ProcessRawEventBatchUseCase.process(List<UUID>)` and `application/port/out`: `RawEventStatePort` (`findById`, `markProcessed(rawEventId, eventId, ciId)`, `recordError`), `EventStorePort` (`save`, `findById`, `findActiveDuplicate(DedupKey)`, `countRecentBySeverity`, `existsRecentByTitle`, `findWindow`), `CiLookupPort`, `RuleDefinitionPort` (`findEnabledRules`, `markRun(Set<UUID>, Instant)`), `NotificationPort.notify(ruleId, channel, emailAddress)`, `PushNotificationPort.createPush(ruleId, eventId, title, message)`

  All seven ports declared with exactly these signatures. **`EventStorePort` does not implement `ThresholdEvaluator.Window` / `CorrelationEvaluator.Window`**, contrary to the group-6 carry-forward: the port speaks in persistence terms (`countRecentBySeverity(sourceId, ciId, severity, since)`) while the windows speak in rule terms (`countRecentCritical(sourceId, ciId, since)`), and folding the literal `"critical"` into the port would have moved a rule decision into the adapter. `ProcessRawEventBatchService` adapts instead, via two private inner classes — which is the alternative the carry-forward allowed and keeps the short-circuit ordering in the evaluator where group 6 put it.

- [x] 7.2 Write Spring-free `ProcessRawEventBatchServiceTest` with fakes of all six ports: event creation from raw, CI binding, dedup branch vs direct save, threshold intent, correlation intent setting `rootEventId`, notify and push intents, `markRun` of executed rules, `markProcessed`, skipping `processed = true`, and an exception in one raw event calling `recordError` without aborting the batch or escaping the method — red

  18 cases (the listed ones plus an empty/null batch, an unknown raw event id, a threshold below the count, a suppressed synthetic, and a failure after the CI lookup that still carries the resolved `ciId`). Red first: `javac` reported `cannot find symbol: class ProcessRawEventBatchService`. Seven port fakes, not six — the task text undercounts, `RuleDefinitionPort` and `PushNotificationPort` are separate. Fakes live in `InMemoryProcessingPorts`, hand-written, no Mockito.

- [x] 7.3 Implement `application/service/ProcessRawEventBatchService` reproducing `EventProcessingService.processBatch`/`processRawEvent` step for step; green for 7.2

  Step-for-step, including the `try/catch` that records an error for one raw event without aborting the batch, and the `ciId` local that is assigned before the failure so `recordError` can still carry it. `EventFactory` and `PushMessageRenderer` are now wired in, as the carry-forward required — `EventProcessingService`'s inline event construction and its private `resolvePushMessage` are both gone. The service takes `Clock` and carries no Spring annotation.

- [x] 7.4 Create `adapter/out/persistence`: `EventJpaEntity` (from `processing/domain/EventEntity`, table `events`, `tags`/`attributes` jsonb `String` mappings byte-for-byte identical), `EventActionLogJpaEntity` (from `EventActionLogEntity`, table `event_action_logs`), `EventJpaRepository`, `EventActionLogJpaRepository`, `EventJpaMapper`

  All five created. Annotations, `columnDefinition` strings and `@JdbcTypeCode(SqlTypes.JSON)` copied verbatim; `EventJpaRepository` keeps `JpaSpecificationExecutor` because the console query service needs it. `EventJpaMapper` is the straight field-for-field copy the carry-forward predicted, in both directions including `id`/`createdAt`/`updatedAt`.

- [x] 7.5 Implement `EventPersistenceAdapter`, porting the `CiId` vs `CiIdIsNull` branching from `DedupService.findActiveDuplicate` **verbatim** — including the redundant-looking `if (ciId == null && config.useCi())` branch — plus the 4 threshold queries and 6 correlation window queries with their `OrderByCreatedAtAsc` ordering; verify against the group-1 characterization tests

  All 11 queries ported unchanged, redundant middle dedup branch included, with a comment saying why it is kept. Verified against the group-1 tests, which were retargeted rather than rewritten (see 7.10): `DedupQueryCharacterizationTest` 32 cases and `ThresholdQueryCharacterizationTest` 16 cases pass against the adapter, with the same query names, the same argument tuples and the same short-circuit ordering. Both D4 surprises still hold.

- [x] 7.6 Implement `adapter/out/ingestion/RawEventStateAdapter` over `ingestion`'s `RawEventJpaRepository` (single `raw_events` mapping preserved), `adapter/out/cmdb/CiLookupAdapter` over `cmdb.service.CiService`, `adapter/out/notification/NotifyAdapter` and `PushNotificationAdapter`

  All four created. `processing` reaching into `ingestion`'s repository is the one cross-context dependency here and it is confined to this adapter, so the `raw_events` mapping stays single. ArchUnit's layered rules do not object, because a dependency from `processing.adapter..` into `ingestion.adapter..` sits in no layer of either check.

- [x] 7.7 Implement `adapter/out/rules/RuleDefinitionAdapter` over `rules.persistence.ProcessingRuleRepository`, absorbing the canvas-JSON `ObjectMapper` parsing (unparseable canvas still degrades to an empty graph), the plan cache with identical semantics (key `ruleId`, invalidated by comparing `updatedAt`, thread-safety unchanged), and the `last_run_at` update from `RuleCanvasEngine.updateLastRunAt`

  Cache semantics preserved: same `ConcurrentHashMap`, same `ruleId` key, same `updatedAt` comparison, same degradation to an empty graph. One ordering fix over a literal port: the cache is consulted **before** the canvas JSON is parsed, where a literal transcription would have parsed on every call and thrown the result away on a hit. Same observable behavior, one less parse.

- [x] 7.8 Change `notifications.api.NotifyStubService.execute(...)` to take `(UUID ruleId, String channel, String emailAddress)` so `notifications` no longer imports `ru.wisla.fm.processing`; keep it a no-op stub

  Done, still a no-op. Verified by grep that nothing under `notifications/**` imports `ru.wisla.fm.processing` any more.

- [x] 7.9 Add local canvas view records inside `rules` and remove `rules.api.RuleCanvasValidator`'s import of `processing.canvas`; keep the REST contract and every validation error message string identical (`RuleCanvasValidatorTest` unchanged apart from imports)

  `rules/api/CanvasNodeView` and `CanvasEdgeView` added as local copies; the validator's logic and every message string are untouched. **`RuleCanvasValidatorTest` needed no change at all**, not even imports — it only ever referenced the views through `RuleCanvasValidator`'s own signatures. Verified by grep that nothing under `rules/**` imports `ru.wisla.fm.processing` any more.

- [x] 7.10 Point `ingestion`'s `ProcessRawEventBatchAdapter` at `ProcessRawEventBatchUseCase`; create `processing/infrastructure/config` declaring `ProcessRawEventBatchService` and the domain services as beans; delete `processing/service/EventProcessingService`, `DedupService`, `ThresholdService`, `CorrelationService`, `processing/canvas/RuleCanvasEngine`, `processing/domain/EventEntity`, `EventActionLogEntity`, `processing/persistence/*`

  Adapter rewired, `ProcessingConfig` declares the use case and all nine domain services. Everything on the deletion list is gone, plus the four orphans the carry-forward named (`canvas/DedupConfig`, `ThresholdConfig`, `CorrelationConfig`, and `canvas/CanvasNodeView` / `CanvasEdgeView` after 7.9) — `processing/canvas` and `processing/persistence` are now empty and removed.

  **Four test classes lost their subject and were retargeted, none weakened.** `RuleCanvasEngineTest` moved to `processing/domain/service` and now drives `RuleGraphTraverser`, keeping all 4 cases (task 9.2 wants `RuleCanvas*Test` alive). The three group-1 characterization tests moved to `processing/adapter/out/persistence` as `DedupQueryCharacterizationTest` (32), `ThresholdQueryCharacterizationTest` (16) and `CorrelationQueryCharacterizationTest` (18), each driving the evaluator plus `EventPersistenceAdapter` through the same steps the use case performs; `CharacterizationEventRepository` moved with them and now proxies `EventJpaRepository`. Two assertion shapes had to change, both because the adapter maps rather than returns its argument: `assertThat(saved).isSameAs(candidate)` became field equality, and `ThresholdService.evaluateAfterProcessing(event, false)` — a branch that no longer exists, since a disabled threshold is now simply the absence of a threshold intent — became `aDecisionWithoutAThresholdIntentIssuesNoCountingQuery` in `ProcessRawEventBatchServiceTest`.

  `IngestTransactionBoundaryTest` also needed a new failure seam, because every domain service group 6 extracted is `final` and so cannot be subclassed the way `RuleCanvasEngine` was. It now injects a `@Primary CiLookupPort` that throws for one `nodeFqdn`, which fails on the first step inside the production `try` and before any `@Transactional` collaborator is entered — so the failure is still the plain in-memory exception the test needs, and it now happens before anything is persisted rather than after. The failing event's `nodeFqdn` differs from the healthy one's; every assertion is unchanged.

  ArchUnit widened as instructed: `IN_SCOPE` now covers `ru.wisla.fm.processing..` for rules 1–5 and 7, plus a new `processingLayersOnlyDependInwards`, taking fm-module from 9 rules to 10. Rule 6 could not follow, so the two transport rules moved to a separate `TRANSPORT_IN_SCOPE` constant scoped to `ingestion` only, with a javadoc explaining that D7 / task 8.2 deliberately leave `processing/api/EventController` outside `adapter/in`. No `@ArchIgnore`, no suppression list.

- [x] 7.11 Run `cd backend/fm-module; mvn test` — green (expect failures only in the out-of-scope consumers, which group 8 fixes)

  As the task predicts, not green — and in Java the failures are **compile** errors, so `mvn test` runs no tests at all until group 8 lands. `mvn -o test-compile` on `JAVA_HOME=C:\java\jdk25` reports errors in exactly eight files, every one of them named by 8.1/8.2: `admin/api/AdminService`, `config/DevDataSeeder`, `configuration/api/SourceService`, `dashboard/api/DashboardService`, `health/api/ProductHealthService`, `processing/api/EventQueryService`, `processing/service/EventActionService`, `processing/service/EventUpdateService`. Every error is `cannot find symbol: EventEntity / EventActionLogEntity / EventRepository / EventActionLogRepository` — no behavior regression, and **zero errors in any file group 7 created or changed**.

  To verify group 7 without doing group 8's work, the migrated hexagon was compiled and run in isolation: all 71 `ingestion` + `processing` main sources compile clean with `javac --release 25`, and the Spring-free tests against them are **203 tests, 0 failures, 0 errors** — including all 10 ArchUnit rules, the 4 retargeted `RuleCanvasEngineTest` cases, `DedupQueryCharacterizationTest` 32, `ThresholdQueryCharacterizationTest` 16 and `ProcessRawEventBatchServiceTest` 18. Two touched test classes could not be exercised because they need the full Spring context: `CorrelationQueryCharacterizationTest` and `IngestTransactionBoundaryTest`. Group 8 must confirm both at 8.5, together with a full-module ArchUnit run — the isolated run imported only hexagon classes, so it could not see an out-of-scope class depending inward.

## 8. Rename-only cleanup for out-of-scope consumers

> Corrections from group 7, found while deleting the old types:
>
> - **8.3 is not rename-only for `CorrelationServiceTest`.** It autowires `CorrelationService` and builds a `canvas/CorrelationConfig`, both deleted by 7.10, so it needs retargeting onto `CorrelationEvaluator` + `EventStorePort` + `CorrelationPolicy`. `CorrelationQueryCharacterizationTest` is the ready-made pattern — copy its private `applyCorrelation` helper.
> - **`AbstractFmModuleTest` and `EventController` need no edit.** Neither names any of the deleted types; they only broke transitively through `DevDataSeeder` and the console services. 8.2's mention of `EventController` and 8.3's of `AbstractFmModuleTest` are no-ops.
> - `RuleCanvasRuntimeIntegrationTest` really is rename-only (`EventRepository` → `EventJpaRepository`), as are the four controller tests and the two push-notification tests.
> - Group 7 already retargeted `RuleCanvasEngineTest` and the three group-1 characterization tests; they are not group 8's problem.

- [x] 8.1 Update `dashboard/DashboardService`, `admin/AdminService`, `configuration/SourceService`, `health/ProductHealthService` and `config/DevDataSeeder` to `EventJpaEntity`/`EventJpaRepository` — import and type-name changes only, no logic change

  All five updated, import and type name only — 3 changed lines each in `AdminService`, `SourceService` and `DashboardService` (import + field + constructor parameter), 5 in `DevDataSeeder` (those plus `new EventEntity()` → `new EventJpaEntity()`) and 10 in `ProductHealthService`, which also names the entity in three private helper signatures and a method reference. No statement was reordered, no expression rewritten, no import added or removed beyond the renamed ones. Verified before editing that `EventJpaEntity` and `EventActionLogJpaEntity` expose the identical setter/getter set the callers use and that `EventJpaRepository` still declares `countActiveBySeverity`, `existsByCiId`, `existsBySourceId` and `findActiveByCiIds`, so every call site is a drop-in.

- [x] 8.2 Update the console services inside `processing` (`api/EventQueryService`, `service/EventActionService`, `service/EventUpdateService`, `api/EventController`) to `EventJpaEntity`/`EventJpaRepository`/`EventActionLogJpaEntity` — import and type-name changes only; they intentionally keep using the JPA repository directly rather than moving onto ports

  Three services updated (18 changed lines in `EventQueryService`, 13 in `EventActionService`, 5 in `EventUpdateService`); all four `Specification<EventEntity>` generics, both `toDto` overloads and the `EventEntity::getId` method reference became `EventJpaEntity`. Per D7 none of them moved onto a port: they still autowire `EventJpaRepository` / `EventActionLogJpaRepository` directly, and `EventJpaRepository` keeps `JpaSpecificationExecutor` for `EventQueryService.listEvents`.

  **`EventController` needed no edit**, confirming the group-7 correction: grep shows it names none of the deleted types and no `processing.domain` / `processing.persistence` package at all — it broke only transitively through the three services it calls. That list entry is a no-op.

- [x] 8.3 Update `DashboardControllerTest`, `AdminControllerTest`, `SourceControllerTest`, `ProductHealthControllerTest`, `EventControllerTest`, `RuleCanvasRuntimeIntegrationTest`, `CorrelationServiceTest`, the push-notification tests and `AbstractFmModuleTest` by imports and type names only

  Only **two** of the nine listed test classes actually reference a deleted type. `RuleCanvasRuntimeIntegrationTest` is the rename-only one the correction predicted (2 lines: import + `@Autowired` field). The other seven — the four controller tests, `EventControllerTest`, both push-notification tests and `AbstractFmModuleTest` — needed **no edit**: a grep of `src/test` for every type 7.10 deleted (`EventEntity`, `EventActionLogEntity`, `EventRepository`, `EventActionLogRepository`, `EventProcessingService`, `DedupService`, `ThresholdService`, `CorrelationService`, `RuleCanvasEngine`, `DedupConfig`, `ThresholdConfig`, `CorrelationConfig`, `processing.canvas`, `processing.persistence`, and the deleted `ingestion.api` / `ingestion.kafka` / `ingestion.persistence` packages) returns them nowhere; they reach the events table only through MockMvc and the REST contract. Every remaining grep hit outside the two files is javadoc prose naming a superseded class, not a code reference.

  **`CorrelationServiceTest` was retargeted, not renamed**, as the group-7 correction required: it autowired the deleted `CorrelationService` and built a deleted `canvas/CorrelationConfig`. It now autowires `EventStorePort`, constructs `CorrelationEvaluator` directly and drives it through a private `applyCorrelation` helper copied from `CorrelationQueryCharacterizationTest`, with `CorrelationConfig(2, 10, "title")` becoming `CorrelationPolicy(2, 10, "title")` and `EventEntity` becoming the domain `Event`. **Not weakened:** same class name and package, same single test name `linksSecondEventToRootWithinWindow`, same setup (two events sharing source / CI / title), same two `evaluate` calls in the same order including the no-op one on `first`, and the same assertion `updatedSecond.getRootEventId() == first.getId()` — still resolved through a reload from the store rather than from the in-memory object.

- [x] 8.4 Commit this group on its own so the diff reads as a pure rename

  **Not committed** — the user commits explicitly, so this is left staged for them. The group is ready to be committed as a pure rename: the working tree holds exactly 10 files from this group, 8 main and 2 test, and apart from `CorrelationServiceTest` every one is an import plus a type name with no statement, expression or annotation touched. `git diff --stat` for the group's own files is 102 insertions against the same count of deletions in kind — no net logic. `CorrelationServiceTest` is the one file a reviewer must read as more than a rename, for the reason recorded at 8.3.

- [x] 8.5 Run `cd backend/fm-module; mvn test` — green

  `mvn -o clean test` on `JAVA_HOME=C:\java\jdk25`: **321 tests, 0 failures, 0 errors, 0 skipped, exit 0, BUILD SUCCESS** — the full module suite, not a subset, so an out-of-scope class depending inward would have been caught. That is 303 at 6.9 plus 18, consistent with the group-7 net: `ProcessRawEventBatchServiceTest` 18 and `HexagonalArchitectureTest` +1 rule added, the four deleted-subject characterization/engine classes rebalanced.

  The two classes group 7 could not exercise are both **confirmed green**: `IngestTransactionBoundaryTest` 1 and `CorrelationQueryCharacterizationTest` 18. The full **`HexagonalArchitectureTest` run is 10 tests, 0 failures** — all ten rules including the new `processingLayersOnlyDependInwards` hold against the whole `ru.wisla.fm` production tree, so nothing outside the hexagon depends inward in a way the rules forbid. Every class 9.2 names is present and passing: `EventControllerTest` 11, `RuleCanvasRuntimeIntegrationTest` 2, `RuleCanvasEngineTest` 4, `RuleCanvasCompilerTest` 3, `RuleCanvasValidatorTest` 4, `CorrelationServiceTest` 1, `PushNotificationIntegrationTest` 1, `PushNotificationServiceTest` 1, `DashboardControllerTest` 2, `SourceControllerTest` 7, `AdminControllerTest` 10, `ProductHealthControllerTest` 5, `RuleControllerTest` 14, plus the ingestion set `IngestControllerTest` 3, `RawEventKafkaConsumerTest` 3, `RawEventKafkaListenerTest` 7, `RawEventEnvelopeTest` 2.

  `cd backend/adapter; mvn -o clean test` re-run to confirm no cross-effect: **174 tests, 0 failures, 0 errors, exit 0**.

## 9. Full verification and contract regression

- [x] 9.1 Run `cd backend/adapter; mvn test` and `cd backend/fm-module; mvn test` — both green, ArchUnit rules 1–8 passing in both modules (unless 1.3 escalated)

  `mvn -o clean test` on `JAVA_HOME=C:\java\jdk25`, run **after** the 9.8 deletion so these are the final numbers:

  - `backend/adapter`: **174 tests, 0 failures, 0 errors, 0 skipped**, `BUILD SUCCESS`, **exit 0** — identical to 5.6 and 8.5, because 9.8 deleted nothing in this module.
  - `backend/fm-module`: **320 tests, 0 failures, 0 errors, 0 skipped**, `BUILD SUCCESS`, **exit 0** — 321 at 8.5 minus exactly the 1 test of the `ArchUnitBytecodeSmokeTest` that 9.8 removed. The delta is fully accounted for; no other class changed count.

  ArchUnit: `com.wisla.fm.adapter.architecture.HexagonalArchitectureTest` **9 tests, 0 failures**; `ru.wisla.fm.architecture.HexagonalArchitectureTest` **10 tests, 0 failures**. Rules 1–8 are covered in both; the rule-count asymmetry is the two documented splits, not a gap — the adapter splits rule 6 into a class-level and a method-level twin (5.1), and fm-module does that *and* splits rule 7 into `ingestionLayersOnlyDependInwards` + `processingLayersOnlyDependInwards` (7.10). Rule 8 is `adapterServiceDoesNotDependOnFmModule` / `fmModuleDoesNotDependOnTheAdapterService`, still the only module-wide rule in each file.
- [x] 9.2 Confirm no test was deleted, disabled, or had an assertion relaxed: `WebhookControllerTest`, `InternalControllerTest`, `HealthControllerTest`, `BufferRetryWorkerTest`, `RawEventKafkaPublisherTest`, `RawEventEnvelopeCodecTest`, `IngestControllerTest`, `IngestServiceTest`, `RawEventKafkaConsumerTest`, `RawEventKafkaListenerTest`, `RawEventEnvelopeTest`, `EventControllerTest`, `RuleCanvasRuntimeIntegrationTest`, `RuleCanvas*Test`, `CorrelationServiceTest`, `PushNotification*Test`, `DashboardControllerTest`, `SourceControllerTest`, `AdminControllerTest`, `ProductHealthControllerTest`, `RuleControllerTest` all still present and passing

  **Every class on the list is present and green.** Verified two ways: a mechanical `@Test` / `@ParameterizedTest` / `@RepeatedTest` count of each class against `git show HEAD:<old path>`, and the per-class surefire report from the 9.1 run. Surefire counts (all `Failures: 0, Errors: 0, Skipped: 0`):

  | Class (list order) | Path today | Surefire | vs `HEAD` |
  |---|---|---|---|
  | `WebhookControllerTest` | `ingest/adapter/in/web` | 11 | 11 = 11 |
  | `InternalControllerTest` | `ingest/adapter/in/web` | 12 | 12 = 12 |
  | `HealthControllerTest` | `ingest/adapter/in/web` | 1 | 1 = 1 |
  | `BufferRetryWorkerTest` → **`BufferRetrySchedulerTest`** | `ingest/adapter/in/scheduler` | 3 | 3 = 3 |
  | `RawEventKafkaPublisherTest` | `ingest/adapter/out/kafka` | 3 | 3 = 3 |
  | `RawEventEnvelopeCodecTest` | `ingest/adapter/out/kafka` | 3 | 3 = 3 |
  | `IngestControllerTest` | `ingestion/adapter/in/web` | 3 | 3 = 3 |
  | `IngestServiceTest` → **`IngestEventsServiceIntegrationTest`** | `ingestion/application/service` | 2 | 2 = 2 |
  | `RawEventKafkaConsumerTest` | `ingestion/adapter/in/messaging` | 3 | 3 = 3 |
  | `RawEventKafkaListenerTest` | `ingestion/adapter/in/messaging` | 7 | 4 → **7 (+3)** |
  | `RawEventEnvelopeTest` | `ingestion/adapter/in/messaging` | 2 | 2 = 2 |
  | `EventControllerTest` | `processing/api` | 11 | 11 = 11 |
  | `RuleCanvasRuntimeIntegrationTest` | `processing` | 2 | 2 = 2 |
  | `RuleCanvasCompilerTest` | `processing/domain/service` | 3 | 3 = 3 |
  | `RuleCanvasEngineTest` | `processing/domain/service` | 4 | 4 = 4 |
  | `RuleCanvasValidatorTest` | `rules/api` | 4 | 4 = 4 |
  | `CorrelationServiceTest` | `processing/service` | 1 | 1 = 1 |
  | `PushNotificationIntegrationTest` | `notifications/api` | 1 | 1 = 1 |
  | `PushNotificationServiceTest` | `notifications/api` | 1 | 1 = 1 |
  | `DashboardControllerTest` | `dashboard/api` | 2 | 2 = 2 |
  | `SourceControllerTest` | `configuration/api` | 7 | 7 = 7 |
  | `AdminControllerTest` | `admin/api` | 10 | 10 = 10 |
  | `ProductHealthControllerTest` | `health/api` | 5 | 5 = 5 |
  | `RuleControllerTest` | `rules/api` | 14 | 14 = 14 |

  Also counted and unchanged, because the same moves touched them: `RuleConditionEvaluatorTest` 4 = 4 and `SwitchBranchSelectorTest` 2 = 2 (both now in `processing/domain/service`).

  **The single count change is an increase, not a relaxation.** `RawEventKafkaListenerTest` 4 → 7 is the +3 recorded at 4.12: all four original behaviors are kept, and three cases were *added* for the policy branches the listener now decides after 4.9 dropped its `EventSourceRepository` injection (inactive source, null `sourceId`/`body` envelope, `IllegalArgumentException` from the use case).

  **Rename / successor mapping (explicit, as required):**

  | Original | Successor | Kind | Evidence the assertions survived |
  |---|---|---|---|
  | `service/BufferRetryWorkerTest` | `ingest/adapter/in/scheduler/BufferRetrySchedulerTest` | rename, subject renamed `BufferRetryWorker` → `BufferRetryScheduler` (3.11/3.14) | 3 = 3 methods; 3.14 diffed the method sets against `HEAD` |
  | `ingestion/api/IngestServiceTest` | `ingestion/application/service/IngestEventsServiceIntegrationTest` | retargeted, subject `IngestService` deleted by 4.10 | 2 = 2 methods; drives `IngestEventsUseCase` via `IngestRequest.toCommand(sourceId)`, so the original input shape is preserved *and* the DTO→command mapping is additionally covered (4.12) |
  | `service/IngestPayloadMapperCharacterizationTest` (group 1, temporary) | `ingest/domain/IngestPayloadNormalizerTest` | superseded; original removed at 3.15 because 3.14 deletes its subject `service/IngestPayloadMapper` | 56 cases → **57 surefire cases** — every assertion mirrored plus one extra (2.3). The original never reached a commit, so it cannot be diffed against `HEAD`; the 56 → 57 count and the assertion-by-assertion mirror are recorded at 2.3 and 3.15 |
  | `processing/service/CorrelationServiceTest` | same name and package, retargeted onto `CorrelationEvaluator` + `EventStorePort` (8.3) | retargeted, subject `CorrelationService` deleted by 7.10 | 1 = 1 method, same test name, same setup, same two `evaluate` calls, same assertion resolved through a store reload |
  | `processing/canvas/RuleCanvas{Compiler,Engine}Test`, `RuleConditionEvaluatorTest`, `SwitchBranchSelectorTest` | `processing/domain/service/…` | package move (6.8/7.10) | 3/4/4/2 unchanged; `RuleCanvasEngineTest` now drives `RuleGraphTraverser` with all 4 cases intact |
  | `kafka/*Test`, `web/*Test` (adapter), `ingestion/{api,kafka}/*Test` (fm-module) | `ingest/adapter/{in,out}/…`, `ingestion/adapter/in/…` | package move only | counts identical in every case (table above) |

  **No test is disabled or conditionally skipped anywhere.** A scan of both `src/test` trees for `@Disabled`, `@Ignore`, `assumeTrue`, `assumeThat`, `assumingThat` returns zero hits, and both surefire summaries report `Skipped: 0`.

  **Observation, recorded rather than acted on: `CorrelationServiceTest`'s name is now vestigial.** The production class it names (`processing/service/CorrelationService`) was deleted by 7.10, and its single case `linksSecondEventToRootWithinWindow` is a subset of `CorrelationQueryCharacterizationTest` (18 cases), which covers the same window/root behavior plus the six `CiId`/`CiIdIsNull` query variants. This task requires the class present, so it is **kept unchanged**. Renaming it to match its real subject is a tidy-up for a follow-up change, not a 9.x edit.
- [x] 9.3 Verify **service independence**: no file under `backend/adapter/src` imports `ru.wisla.fm..`, no file under `backend/fm-module/src` imports `com.wisla.fm.adapter..`, no new Maven module or shared jar exists under `backend/`, and neither pom depends on the other service

  All four hold, and the greps are clean in the strongest possible way — **the only occurrence of the other service's package name in each module is the ArchUnit rule-8 pattern string itself**:

  - `backend/adapter` searched for `ru.wisla.fm`: 1 hit, `src/test/.../architecture/HexagonalArchitectureTest.java:163` — `.should().dependOnClassesThat().resideInAnyPackage("ru.wisla.fm..")`. Zero hits in `src/main`, zero imports anywhere, zero hits in the pom.
  - `backend/fm-module` searched for `com.wisla.fm.adapter`: 1 hit, `src/test/.../architecture/HexagonalArchitectureTest.java:202` — the mirror pattern. Zero hits in `src/main`, zero imports anywhere, zero hits in the pom.
  - **Maven module inventory under `backend/` is unchanged: exactly three poms** — `adapter/pom.xml`, `fm-module/pom.xml`, `zabbix-simulator/pom.xml`. There is **no `backend/pom.xml`** aggregator, and `git status --porcelain -- "backend/**/pom.xml"` reports only the two `M` entries from 1.1, so no pom was added.
  - Neither pom names the other's coordinates. `adapter/pom.xml` declares `com.wisla.fm:adapter` and mentions no `ru.wisla` / `fm-module`; `fm-module/pom.xml` declares `ru.wisla:fm-module` and mentions no `com.wisla`. No shared jar, no `<module>`, no `<parent>` relationship between them.
- [x] 9.4 Verify each module builds standalone: `mvn test` in `backend/adapter` on a clean local repository state without building `backend/fm-module`, and vice versa

  Proven by construction and by resolution, not just by ordering:

  - Each 9.1 run was `mvn -o clean test` invoked **from the module directory with that module's own pom** — no reactor, no aggregator (there is no `backend/pom.xml`, see 9.3), so Maven never had the other module in the build graph. `backend/adapter` was built and tested first, `BUILD SUCCESS`, without `backend/fm-module` being built in that invocation; then `backend/fm-module` was built and tested, `BUILD SUCCESS`, without `backend/adapter` being built in that invocation.
  - `-o` (offline) means neither build could have reached out for the other's artifact even if something asked for it.
  - The decisive check is the resolved tree, not the invocation order: `mvn -o dependency:list -DincludeScope=test` resolves **186 artifacts in `backend/adapter`, none of them `ru.wisla:*` or `fm-module`**, and **180 artifacts in `backend/fm-module`, none of them `com.wisla*`**. So neither module resolves the other from the local repository under any scope, which is what "standalone on a clean local repository state" actually requires.
- [x] 9.5 Verify the pom diffs add only `com.tngtech.archunit:archunit-junit5` at `test` scope; run `mvn dependency:list` in each module and confirm no MapStruct and no Testcontainers artifact

  **Both pom diffs are byte-for-byte the same six-line insertion and nothing else.** `git diff -- backend/adapter/pom.xml` and `git diff -- backend/fm-module/pom.xml` each show exactly one hunk, `+6/-0`, inserted after the `h2` test dependency:

  ```xml
  <dependency>
      <groupId>com.tngtech.archunit</groupId>
      <artifactId>archunit-junit5</artifactId>
      <version>1.4.2</version>
      <scope>test</scope>
  </dependency>
  ```

  No other line in either pom changed — no plugin, no property, no `java.version`, no dependency-management entry.

  `mvn -o dependency:list -DincludeScope=test`:

  - **ArchUnit resolves at `test` scope only**, in both modules: `archunit-junit5:1.4.2:test`, plus its four transitives `archunit-junit5-api`, `archunit`, `archunit-junit5-engine`, `archunit-junit5-engine-api`, all `:test`. Nothing ArchUnit-related leaks into `compile` or `runtime`.
  - **No MapStruct artifact** in either module (`mapstruct` matches nothing) — D2's rejection of an annotation processor on `java.version = 25` holds, and every mapper stays hand-written.
  - **No Testcontainers artifact** in either module (`testcontainers` matches nothing) — the H2 + `@EmbeddedKafka` test stack is unchanged.
- [x] 9.6 Verify `git diff` against the base branch contains no change under `backend/*/src/main/resources/db/**`, `docs/**/api.yaml`, `frontend/**`, `prototype/**`, `backend/zabbix-simulator/**`, `backend/docker-compose*.yaml`, `backend/docker/**`

  The base is `HEAD` (`b971b4e`): groups 1–8 were deliberately left uncommitted (1.10, 8.4), so the whole change is the working tree and both `git diff HEAD -- <path>` and `git status --porcelain -- <path>` must be consulted per path. Both are empty for every frozen path:

  | Frozen path | `git diff HEAD` | `git status` |
  |---|---|---|
  | `backend/adapter/src/main/resources/db/**` | clean | clean |
  | `backend/fm-module/src/main/resources/db/**` | clean | clean |
  | `docs/**/api.yaml` (all three: `adapter`, `fm-module`, `zabbix-simulator`) | clean | clean |
  | `frontend/**` | clean | clean |
  | `prototype/**` | clean | clean |
  | `backend/zabbix-simulator/**` | clean | clean |
  | `backend/docker-compose.yaml` / `.yml` | clean | clean |
  | `backend/docker/**` | clean | clean |

  So **no Liquibase changelog, no OpenAPI contract, no frontend, no prototype, no simulator, and no Docker asset was touched** — consistent with the change altering only Java package structure plus one test-scope dependency.

  One `docs/` file is modified — `docs/adr/ADR-001-hexagonal-architecture.md` — which is **group 10's own work in progress, not a 9.6 violation**: this task freezes `docs/**/api.yaml` specifically, and 10.1/10.2 exist precisely to amend that ADR. All three `api.yaml` files are untouched, which is the part 9.6 actually asserts.
- [x] 9.7 Verify the two `RawEventEnvelope` copies still declare identical JSON field names and `schemaVersion = 1` while remaining separate types with different `body` types (`Map<String, Object>` in the adapter, `IngestRequest` in fm-module)

  Both properties hold, and neither file changed in substance since `HEAD`.

  **Identical JSON field names, in identical order** — six components, no `@JsonProperty` rename on either side, so Jackson uses the record component names verbatim:

  | # | adapter `…ingest/adapter/out/kafka/RawEventEnvelope` | fm-module `…ingestion/adapter/in/messaging/RawEventEnvelope` |
  |---|---|---|
  | 1 | `int schemaVersion` | `int schemaVersion` |
  | 2 | `UUID messageId` | `@NotNull UUID messageId` |
  | 3 | `Instant producedAt` | `@NotNull Instant producedAt` |
  | 4 | `UUID sourceId` | `@NotNull UUID sourceId` |
  | 5 | `String sourceKey` | `@NotBlank String sourceKey` |
  | 6 | **`Map<String, Object> body`** | **`@NotNull @Valid IngestRequest body`** |

  **Still two separate, unrelated types**, exactly as D0 requires: different packages in different Maven artifacts, no `extends`/`implements` relationship, no shared interface, and 9.3 proves neither module can even see the other's class. The `body` type difference is intact — the loose producer map vs the validated consumer DTO — so there is nothing to "deduplicate".

  **`schemaVersion = 1` on both sides.** The adapter keeps `public static final int CURRENT_SCHEMA_VERSION = 1`, written by `RawEventEnvelopeCodec` and asserted by `RawEventEnvelopeCodecTest` (`restored.schemaVersion()` is `1`). fm-module carries no constant — unchanged from before the refactor — and pins the wire value in `RawEventEnvelopeTest`, which asserts on the serialized tree: `schemaVersion` is `1`, and `messageId` / `producedAt` / `sourceId` / `sourceKey` serialize under exactly those names. So the frozen JSON shape is guarded on the producer *and* the consumer side.

  **Neither file drifted.** Diffed against `git show HEAD:<old path>`: the adapter copy differs only by its `package` line plus three added javadoc lines; the fm-module copy only by its `package` line, the `IngestRequest` import following that DTO's own move, and one added javadoc line. `@JsonInclude(NON_NULL)` and the four validation annotations are present on the fm-module copy and absent on the adapter copy — the same asymmetry as before the refactor, not a new divergence.
- [x] 9.8 Delete the throwaway ArchUnit smoke tests from 1.2 if any remain, and remove the temporary group-1 characterization tests only where a permanent domain-level test in groups 2, 3, 4, 6 or 7 now covers the same behavior — otherwise keep them

  **Deleted: exactly one file.** `backend/fm-module/src/test/java/ru/wisla/fm/architecture/ArchUnitBytecodeSmokeTest.java` — the 1.2 throwaway, 1 test, its own javadoc calling itself "Throwaway compatibility smoke test … Replaced by the real hexagonal rule set once the migration lands". Its job (proving ArchUnit's ASM reads class file major version 69) is now done far more thoroughly by the 10 real rules in the same package. The adapter's smoke test needed no action: 5.1 already overwrote it in place with the rule set.

  **Kept deliberately — all five remaining characterization tests, and the conservative reading of "only where a permanent test covers the same behavior" is what keeps them:**

  | Kept test | Cases | Why keeping it is right |
  |---|---|---|
  | `ingest/adapter/out/persistence/BufferedMessageBackoffCharacterizationTest` | 5 | `BufferedEventTest` (6) covers `BufferedEvent.scheduleRetry` at the domain level, but this test pins the same `base * 2^min(retryCount - 1, 10)` curve **as it lands in the row**, through `BufferedEventJpaMapper.toEntity`. The mapper hop is behavior no domain test can see. |
  | `ingest/adapter/out/persistence/SourceConfigSnapshotUpsertCharacterizationTest` | 5 | Pins `created_at` surviving `replace(...)` on `SourceConfigSnapshotJpaEntity`. **No domain-level test covers this at all** — the semantics live in the JPA entity, and `SourceConfigStorePort.upsert` is specified in terms of it. |
  | `processing/adapter/out/persistence/DedupQueryCharacterizationTest` | 32 | These three are now the **only** guard for D4. `DedupMergerTest` / `ThresholdEvaluatorTest` / `CorrelationEvaluatorTest` deliberately cannot assert the `CiId` vs `CiIdIsNull` branching, because 6.4/7.1 put the query choice in `EventPersistenceAdapter` and kept only the rule decision in the domain. Deleting them would drop the redundant-middle-branch pin and both D4 surprises. |
  | `…/ThresholdQueryCharacterizationTest` | 16 | as above — the 4 threshold queries, their argument tuples and the count-before-exists short-circuit ordering |
  | `…/CorrelationQueryCharacterizationTest` | 18 | as above — the 6 window queries, plus that an unrecognised `matchField` resolves to the title window |

  They are no longer "temporary" in any case: 3.15 and 7.10 retargeted all five off their deleted subjects and onto the migrated code, so they are permanent adapter-level regressions that sit *below* the domain tests rather than duplicating them.

  **Already removed earlier, recorded here for completeness:** `IngestPayloadMapperCharacterizationTest` (56 cases) went at 3.15, forced — 3.14 deletes its subject `service/IngestPayloadMapper`, so it could not compile — and superseded by `IngestPayloadNormalizerTest` (57 surefire cases), which 2.3 wrote as a mirror of exactly those assertions plus one. That is the one case where 9.8's "a permanent domain-level test now covers the same behavior" is unambiguously satisfied.

  **Additional tidy-up, no test or production impact: 20 empty leftover directories removed.** `git` does not track empty directories, so these were invisible in `git status` but present on disk as hollow shells left by the group 3–8 package moves: `ru/wisla/fm/ingestion/{api,kafka,persistence}` and `ru/wisla/fm/processing/{canvas,persistence}` under `fm-module/src/main`, `ru/wisla/fm/ingestion/{api,kafka}` and `ru/wisla/fm/processing/canvas` under `fm-module/src/test`, and `com/wisla/fm/adapter/{web,service,kafka,persistence/entity}` under `adapter/src/test`, plus their now-childless parents. **Two of them were worth removing on principle, not just tidiness:** `backend/adapter/src/main/java/ru/wisla/fm` and `backend/fm-module/src/main/java/com/wisla/fm/adapter` are the residue of the 5.5 rule-8 probes — empty package trees sitting in the *other* service's namespace inside a module, which is precisely the shape D0 forbids and an invitation for someone to drop a "shared" class into. Both are gone; zero empty directories remain under either `src`. Also confirmed the 5.5 orphaned-`.class` trap has not recurred: no stale probe class files under either `target/`, and both 9.1 runs were `clean` anyway.
- [x] 9.9 Skip frontend verification: `cd frontend; npm test` and `npm run test:e2e` are not required because `frontend/` is untouched (`tests.frontend = skipped`, `tests.frontend_e2e = skipped`)

  Skipped, and the premise is verified rather than assumed: 9.6 shows `frontend/**` is clean in both `git diff HEAD` and `git status --porcelain`, so not one file under `frontend/` was modified, added or deleted by this change. `npm test` and `npm run test:e2e` were not run. `tests.frontend = skipped`, `tests.frontend_e2e = skipped`.

## 10. Documentation

- [x] 10.1 Amend `docs/adr/ADR-001-hexagonal-architecture.md`: record `com.wisla.fm.adapter.ingest`, `ru.wisla.fm.ingestion` and `ru.wisla.fm.processing` as the completed pilot, supersede the "ArchUnit or other automated architecture enforcement" and "Moving or renaming existing production packages" non-goals for those contexts, and keep "A shared domain JAR between deployables" as a standing non-goal

  Amended, not rewritten: the Decision section, the layer-responsibility table and the six-part design checklist are untouched. Added one section, **"Pilot outcome (amendment, 2026-08-03)"**, between Consequences and Alternatives, naming the three contexts, the structure they now have (with `domain/service/` — the one element the original target-structure block does not list, added in the pilot block rather than in the normative Decision block), the ten unmigrated contexts, and the fact that no REST/Kafka/DDL/Liquibase contract changed with 174 + 321 tests green.

  Both non-goals are marked superseded **in place, scoped to the three contexts**, each with the date and a "still a non-goal elsewhere" clause, so the bullet still reads as normative for the rest of the codebase. "A shared domain JAR between deployables" is explicitly re-affirmed as a standing non-goal with a pointer to the new "Service independence" subsection. Three older statements that the pilot contradicts are marked rather than silently left: `Status` now reads "Accepted. Amended on 2026-08-03 …", the Context sentence "It does not reorganize the existing implementation" is annotated as true-as-accepted / superseded for the three contexts, and the rejected alternative "Add ArchUnit enforcement now" carries a one-line supersession note. Nothing was deleted from the ADR.

  The **Enforcement** subsection records both deliberate limitations rather than hiding them: rule 6 is scoped to `ingestion` only because D7 leaves `processing/api/EventController` and the console services outside `adapter/in` for this change, and the layered rule permits `adapter → infrastructure` for the `@ConfigurationProperties` records while `domain` and `application` stay cut off from it.
- [x] 10.2 Note in the ADR that `backend/adapter` and `backend/fm-module` remain compile-time independent, that each owns a private copy of the wire-contract types, and that ArchUnit rule 8 enforces it

  New **"Service independence"** subsection inside the pilot section: no pom dependency either way, no shared module or jar, no shared Java class; each service owns a private copy of the wire-contract types; the two `RawEventEnvelope` copies are deliberately separate and structurally different (adapter `body` is `Map<String, Object>`, fm-module's is a validated `IngestRequest`) while both keep the same JSON field names and `schemaVersion = 1`; and rule 8 of `HexagonalArchitectureTest` enforces the ban in both directions, quoted as the two package patterns. It closes by linking back to the standing "shared domain JAR" non-goal, so D0 and that non-goal are one argument in the ADR rather than two disconnected statements.
- [x] 10.3 Update `docs/architecture.md` only if it lists package structure for the migrated contexts; make no change to `docs/**/api.yaml`

  **No change needed, and none made.** Read in full and grepped for the migrated contexts. The only package mention is §1.2 "Внутренние bounded contexts", whose "Пакет (целевой)" column holds `.../ingestion` and `.../processing` — context root packages, which the refactor did not move; the internal layout below them is not listed anywhere in the document. §8 "Структура репозитория" lists directories (`backend/adapter/`, `backend/fm-module/`, `frontend/`, `docs/`), not Java packages, and is still accurate. Every other hit is a context name in a routing, domain-event or Kafka-topic table, none of which the refactor touched. `docs/**/api.yaml` not opened and not modified; `git status -- docs` shows exactly one modified file for this group, `docs/adr/ADR-001-hexagonal-architecture.md`. Nothing under `backend/`, `frontend/` or `openspec/` was touched other than these three checkboxes and their notes.

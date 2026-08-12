## Why

`docs/adr/ADR-001-hexagonal-architecture.md` is Accepted but was explicitly "process-only": it never touched the existing implementation. As a result both backend services are still classic layered Spring/JPA — `@Entity` classes live directly in `domain/` packages (`RawEventEntity`, `EventEntity`, `EventActionLogEntity`, `SourceConfigSnapshot`, `BufferedMessage`), use-case services inject Spring Data repositories, `ObjectMapper`, `PasswordEncoder`, `RestClient` and `Authentication`, and the actual business decisions (pre-filter, severity normalization, dedup/threshold/correlation, rule-graph traversal) cannot be tested without booting a Spring context and H2.

This change is the first intentional pilot of ADR-001 against real code: it converts the ingest vertical slice and the processing engine core to ports and adapters **with zero change to external behavior**, and adds ArchUnit so the boundaries stay enforced by the build rather than by review.

## What Changes

### Service independence is a hard constraint (read this first)

`backend/adapter` and `backend/fm-module` are **two independent microservices**. This refactor MUST NOT introduce any compile-time coupling between them:

- No Maven dependency from one module on the other, in either direction.
- No shared `domain` / `contracts` / `common` JAR or Maven module.
- No shared Java classes at all — including the Kafka envelope, ingest DTOs, enums and constants.
- Each service keeps its **own** copy of the types that describe the wire contract. The duplication is a deliberate, documented cost of service independence, not technical debt to be "cleaned up". This is already the status quo and must be preserved: `com.wisla.fm.adapter.kafka.RawEventEnvelope` carries `Map<String, Object> body`, while `ru.wisla.fm.ingestion.kafka.RawEventEnvelope` carries a validated `IngestRequest body`. Two independently-evolvable representations of one wire format is the correct design.
- Any adapter port that points at fm-module (`FmModuleSourceConfigPort`) is an outbound port to an **external system**, implemented in `adapter/out/http`, speaking HTTP. It never imports an fm-module class.
- Integration remains **network-only**: Kafka topic `fm.raw-events` (`RawEventEnvelope`, `schemaVersion = 1`, key = `sourceId`) and the internal HTTP endpoints.
- The temptation to "extract a shared module so we don't duplicate the envelope" is **explicitly rejected**. A shared jar would couple the release cycles of two independently deployable services and is already listed as a non-goal in ADR-001.
- An ArchUnit rule enforces this: no class under `com.wisla.fm.adapter..` may import `ru.wisla.fm..`, and no class under `ru.wisla.fm..` may import `com.wisla.fm.adapter..`.

### Phase 1 — ingest vertical slice

- `backend/adapter`: new bounded context root `com.wisla.fm.adapter.ingest` with `domain`, `application/port/{in,out}`, `application/service`, `adapter/{in,out}`, `infrastructure/config`.
  - Inbound ports `ReceiveWebhookEventUseCase`, `DeliverIngestEventUseCase`, `RetryBufferedEventsUseCase`, `SyncSourceConfigUseCase` replace `WebhookService.receive/deliver`, `BufferRetryWorker.retryBufferedMessages`, `SourceConfigSyncService.syncFromFmModule`.
  - Outbound ports `SourceConfigLookupPort`, `SourceConfigStorePort`, `BufferedEventStorePort`, `RawEventPublisherPort`, `ApiKeyVerifierPort`, `FmModuleSourceConfigPort`.
  - `FilterService` becomes domain behavior on `FilterRules` / `FilterCondition`; `IngestPayloadMapper` becomes `IngestPayloadNormalizer`; buffer backoff moves from `BufferedMessage.scheduleRetry` to `BufferedEvent.scheduleRetry(baseSeconds, now)`; use cases take `java.time.Clock` instead of calling `Instant.now()`.
- `backend/fm-module` context `ingestion`: `IngestEventsUseCase` / `QueryRawEventsUseCase` inbound ports; `RawEventStorePort`, `EventSourceStatePort`, `ProcessRawEventBatchPort` outbound ports. `Authentication` no longer reaches the use case — `IngestController` extracts the `UUID` principal and builds the command. `RawEventKafkaListener` stops reading `EventSourceRepository` itself.
- **Full JPA split**: pure domain models plus `*JpaEntity` classes under `adapter/out/persistence` with hand-written mappers. **No MapStruct.**
- **Transaction behavior unchanged**: `ingest → processBatch` stays one transaction. `@Transactional` moves off `IngestService` onto the inbound adapters; failure behavior, rollback semantics and Kafka commit policy are identical.

### Phase 2 — processing core

- `ru.wisla.fm.processing` gains `domain` + `domain/service` (`Event`, `IncomingRawEvent`, `SeverityRank`, `DedupPolicy`, `DedupKey`, `ThresholdPolicy`, `CorrelationPolicy`, `ProcessingDecision`, `CompiledRulePlan`, `RuleGraph`; `RuleGraphTraverser`, `RuleCanvasCompiler`, `RuleConditionEvaluator`, `SwitchBranchSelector`, `EventFactory`, `DedupMerger`, `ThresholdEvaluator`, `CorrelationEvaluator`, `PushMessageRenderer`).
- Inbound port `ProcessRawEventBatchUseCase`; outbound ports `EventStorePort`, `RawEventStatePort`, `CiLookupPort`, `RuleDefinitionPort`, `NotificationPort`, `PushNotificationPort`.
- `RuleCanvasEngine`'s canvas JSON parsing and its `HashMap` plan cache move into `RuleDefinitionAdapter` with identical invalidation semantics (`ruleId` + `updatedAt`).
- Reverse dependencies removed: `NotifyStubService.execute(...)` takes primitives instead of `ProcessingDecision.NotifyIntent`; `rules.api.RuleCanvasValidator` gets local view records instead of importing `processing.canvas`.

### ArchUnit enforcement

- `com.tngtech.archunit:archunit-junit5` (test scope) added to **both** poms; `HexagonalArchitectureTest` in each module.
- fm-module rules are scoped **strictly** to `ru.wisla.fm.ingestion..` and `ru.wisla.fm.processing..` so the ten unmigrated bounded contexts do not fail the build.
- **BREAKING (process only, no runtime impact)**: ADR-001 currently lists "ArchUnit or other automated architecture enforcement" and "Moving or renaming existing production packages" as non-goals. This change deliberately supersedes both for the two piloted contexts, so ADR-001 needs an amendment recording the pilot outcome.

## Capabilities

### New Capabilities

- `architecture-service-independence`: `backend/adapter` and `backend/fm-module` remain independently buildable and deployable — no Maven dependency, no shared module, no shared Java type between them; integration is only via Kafka `fm.raw-events` and internal HTTP; each service owns its private copy of the wire-contract types.
- `architecture-hexagonal-ingest`: the ingest vertical slice (`com.wisla.fm.adapter.ingest`, `ru.wisla.fm.ingestion`) follows ADR-001 layering with named inbound/outbound ports, pure domain models, `*JpaEntity` persistence adapters, unchanged frozen REST/Kafka/DB contracts and unchanged transaction boundary.
- `architecture-hexagonal-processing`: the processing core (`ru.wisla.fm.processing`, including the canvas rule runtime) follows ADR-001 layering with six outbound ports into `ingestion`, `cmdb`, `rules` and `notifications`, and preserves the existing dedup/threshold/correlation query behavior verbatim.
- `architecture-enforcement-archunit`: ArchUnit tests enforce the layering rules in both services, Spring-free use-case tests exist for every migrated use case, and an explicit fallback applies if ArchUnit's bytecode reader cannot parse Java 25 class files.

### Modified Capabilities

None. No existing spec in `openspec/specs/` changes its required behavior — that is the point of this change. `adapter-runtime`, `adapter-config-sync`, `kafka-raw-event-ingest` and `rules-canvas-runtime` describe behavior that must be provably identical after the refactor, and their existing tests are the regression net.

## Impact

### Affected modules

| Module | Impact |
|---|---|
| `backend/adapter` | Full internal restructure of the ingest context; `archunit-junit5` added (test scope). No new runtime dependency. |
| `backend/fm-module` | `ingestion` and `processing` restructured; mechanical type/import updates in `dashboard`, `admin`, `configuration`, `health`, `config/DevDataSeeder`, `notifications`, `rules` and the console services inside `processing`; `archunit-junit5` added (test scope). |
| `backend/zabbix-simulator` | Not touched. |
| `frontend/` | Not touched — `tests.frontend` and `tests.frontend_e2e` stay `skipped`. |
| `prototype/` | Not touched. |
| `docs/` | ADR-001 amendment recording the pilot and superseding its ArchUnit / no-package-move non-goals. No `api.yaml` change. |

### Explicitly unaffected

- **Liquibase / SQL**: zero new or modified changesets; `db.changelog-master.yaml` unchanged in both services. Tables `source_config_snapshots`, `buffered_messages`, `raw_events`, `events`, `event_action_logs`, `event_sources`, `configuration_items`, `processing_rules`, `rule_push_notifications` keep identical DDL, including every `@JdbcTypeCode(SqlTypes.JSON)` column.
- **REST / OpenAPI**: no path, method, status code, DTO field name or error code changes. Frozen: `POST /webhook/{sourceKey}` (400 `invalid_json`, 400 `filtered`, 401 `missing_api_key`, 401 `invalid_source_key`, 403 `source_blocked`, 404 `unknown_source`, 413 `payload_too_large`, 502 `ingest_rejected`), `GET /health`, `GET /internal/sources/{sourceId}/config`, `POST /internal/probe`, `POST /internal/config/sync`, `POST /api/v1/ingest`, `GET /api/v1/raw-events`, `GET|PATCH /api/v1/events...`, `POST /api/v1/events/{id}/actions`, `GET /api/v1/internal/sources`.
- **Kafka**: topic `fm.raw-events`, key = `sourceId.toString()`, `RawEventEnvelope{schemaVersion=1,...}`, consumer group and `AckMode.RECORD` commit/skip policy all unchanged.
- **Angular routes**: none — the SPA is not touched.
- **Docker Compose / `backend/docker/**`**: unchanged environment variables and ports.

### Non-goals

- No REST, Kafka, database, or Liquibase contract change of any kind.
- No frontend, prototype, or `zabbix-simulator` change.
- **No shared domain/contracts JAR or Maven module between the two services**, and no Maven dependency between them.
- No MapStruct; mappers are hand-written.
- No Testcontainers migration; existing `@SpringBootTest` + H2 and `@EmbeddedKafka` tests stay as the regression net.
- No service split and no new deployable unit.
- No strangler-style temporary delegating wrappers (see `design.md` for why direct move was chosen).
- The bounded contexts `identity`, `console`, `dashboard`, `admin`, `settings`, `health`, `configuration`, `cmdb`, `rules` and `notifications` are **not** migrated to hexagonal layering. They are touched only by mechanical renames where an entity they consume was renamed.
- No performance or query-plan optimization; the emitted SQL set stays the same.
- No change to the `RuleCanvasEngine` plan-cache thread-safety (still an unsynchronized map, moved as-is; a follow-up may address it).

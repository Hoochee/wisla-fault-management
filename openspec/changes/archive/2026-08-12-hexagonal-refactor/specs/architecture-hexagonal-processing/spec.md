## ADDED Requirements

### Requirement: Processing context follows the ADR-001 package layout

`ru.wisla.fm.processing` SHALL be organized into `domain`, `domain/service`, `application/port/in`, `application/port/out`, `application/service`, `adapter/out/...`, and `infrastructure/config`. `domain` and `application` SHALL be free of Spring, `jakarta.persistence`, Hibernate, Jackson, Kafka and servlet types. The console REST surface (`api/EventController`, `api/EventQueryService`, `service/EventActionService`, `service/EventUpdateService`) is out of scope and SHALL remain where it is.

#### Scenario: Batch processing is reachable through one inbound port

- **GIVEN** the refactored processing context
- **WHEN** its inbound port is inspected
- **THEN** `ProcessRawEventBatchUseCase.process(List<UUID> rawEventIds)` is the only entry point, implemented by `application/service/ProcessRawEventBatchService`
- **AND** it is the contract that `ingestion` calls through `ProcessRawEventBatchPort`

#### Scenario: Every processing side effect has one port and one implementation

- **GIVEN** `ProcessRawEventBatchService`
- **WHEN** its dependencies are inspected
- **THEN** it depends only on `domain` types and the outbound ports `RawEventStatePort`, `EventStorePort`, `CiLookupPort`, `RuleDefinitionPort`, `NotificationPort` and `PushNotificationPort`
- **AND** each port has exactly one implementation: `adapter/out/ingestion/RawEventStateAdapter`, `adapter/out/persistence/EventPersistenceAdapter`, `adapter/out/cmdb/CiLookupAdapter`, `adapter/out/rules/RuleDefinitionAdapter`, `adapter/out/notification/NotifyAdapter` and `adapter/out/notification/PushNotificationAdapter`
- **AND** no direct reference to `cmdb.service.CiService`, `rules.persistence.ProcessingRuleRepository`, `notifications.api.NotifyStubService` or `notifications.api.PushNotificationService` remains in `application`

#### Scenario: Rule-engine logic becomes domain services

- **GIVEN** the canvas rule runtime currently implemented by `RuleCanvasEngine`
- **WHEN** it is split after the refactor
- **THEN** the graph traversal lives in `domain/service/RuleGraphTraverser`, and `RuleCanvasCompiler`, `RuleConditionEvaluator` and `SwitchBranchSelector` are plain domain services with no Spring stereotype annotations
- **AND** the merge rule from `DedupService`, the threshold rule from `ThresholdService`, the correlation rule from `CorrelationService`, the event-construction logic from `EventProcessingService` and its push-message template become `DedupMerger`, `ThresholdEvaluator`, `CorrelationEvaluator`, `EventFactory` and `PushMessageRenderer`
- **AND** `domain` contains no `ObjectMapper`, repository, or `@Transactional` reference

#### Scenario: Canvas parsing and the plan cache move into the rules adapter

- **GIVEN** `RuleCanvasEngine` today parses canvas JSON with `ObjectMapper` and caches compiled plans in an unsynchronized `HashMap` keyed by `ruleId`
- **WHEN** that responsibility moves to `adapter/out/rules/RuleDefinitionAdapter`
- **THEN** the cache keeps identical semantics: keyed by `ruleId` and invalidated by comparing the rule's `updatedAt`
- **AND** an unparseable canvas still degrades to an empty node/edge graph rather than failing the batch
- **AND** thread-safety behavior is unchanged in this change
- **AND** `RuleCanvasRuntimeIntegrationTest` still proves that updating a rule causes recompilation

#### Scenario: JPA entities leave the domain package

- **GIVEN** `processing/domain/EventEntity` and `processing/domain/EventActionLogEntity`
- **WHEN** the refactor is complete
- **THEN** they exist as `adapter/out/persistence/EventJpaEntity` and `adapter/out/persistence/EventActionLogJpaEntity`
- **AND** the tables `events` and `event_action_logs`, all column names, `columnDefinition` values and `@JdbcTypeCode(SqlTypes.JSON)` declarations on `events.tags` and `events.attributes` are unchanged
- **AND** the domain `Event` keeps `attributes` as a JSON `String` so nothing is re-serialized
- **AND** `git diff` shows no change under `backend/fm-module/src/main/resources/db/**`

### Requirement: Dedup, threshold and correlation query behavior is preserved verbatim

The branching between the `CiId` and `CiIdIsNull` derived-query variants in `EventRepository` SHALL be reproduced literally in `EventPersistenceAdapter`, including the case where `useCi = false` still resolves to the `CiIdIsNull` variant. No query in the dedup, threshold, or correlation paths SHALL be added, removed, merged, or "corrected".

#### Scenario: Dedup lookup keeps its surprising useCi behavior

- **GIVEN** a dedup policy with `useCi = false` and a candidate event whose `ciId` is not null
- **WHEN** `EventStorePort.findActiveDuplicate(DedupKey)` runs
- **THEN** the `findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn` variant is used, exactly as the current `DedupService.findActiveDuplicate` does
- **AND** characterization tests cover all four combinations of `useSource`, `useTitle` and `useCi` against both `ciId = null` and `ciId != null`
- **AND** those characterization tests are written and passing against the current code before any class is moved

#### Scenario: Dedup with no keys creates a new event

- **GIVEN** a dedup policy where `useSource`, `useTitle` and `useCi` are all disabled
- **WHEN** the candidate event is processed
- **THEN** no duplicate lookup is performed and a new event is saved
- **AND** the active-status set `["new", "in_progress", "maintenance", "deferred"]` used for duplicate lookup is unchanged

#### Scenario: Merging a duplicate keeps its counters and severity rule

- **GIVEN** an active duplicate event is found
- **WHEN** `DedupMerger` merges the candidate into it
- **THEN** `repeat_count` is incremented by one and `last_repeat_at` is set to the current time
- **AND** the severity is raised only when the incoming severity ranks more severe, using the order `fatal < critical < major < minor < warning < other`

#### Scenario: Threshold and correlation queries are ported unchanged

- **GIVEN** the four threshold counting/existence queries and the six correlation window queries
- **WHEN** they move behind `EventStorePort`
- **THEN** each keeps its `CiId` and `CiIdIsNull` variant and its `OrderByCreatedAtAsc` ordering
- **AND** the synthetic threshold event keeps the title `"Threshold: N+ critical events in M minutes"`, `severity = fatal` and `attributes = {"synthetic":true,"ruleType":"threshold"}`
- **AND** the synthetic event remains idempotent by title within the window
- **AND** correlation still supports match fields `title`, `severity` and `source`, uses the first event in the window as root, adopts an existing `rootEventId`, and never creates a self-reference

### Requirement: Batch processing behavior and error isolation are unchanged

`ProcessRawEventBatchService` SHALL reproduce the current `EventProcessingService.processBatch` / `processRawEvent` algorithm, including skipping already-processed raw events and swallowing per-event exceptions into `raw_events.processing_error` without aborting the batch or the surrounding transaction.

#### Scenario: A raw event is turned into an event with CI enrichment

- **GIVEN** an unprocessed raw event whose `nodeFqdn` resolves to a configuration item
- **WHEN** the batch is processed
- **THEN** the created event carries `status = "new"`, and the severity, title, description, `sourceId`, `nodeFqdn`, `rawEventId`, `sourceAt` and attributes of the raw event
- **AND** `ciId`, `systemName` and `subsystemName` are copied from the configuration item
- **AND** the raw event's `ciId` is recorded as well
- **AND** `RawEventStatePort.markProcessed(rawEventId, eventId, ciId)` is called

#### Scenario: Already-processed raw events are skipped

- **GIVEN** a raw event whose `processed` flag is already true
- **WHEN** the batch containing it is processed
- **THEN** it is skipped without creating an event and without any port write

#### Scenario: One failing raw event does not abort the batch

- **GIVEN** a batch of raw events where processing one of them throws
- **WHEN** the batch is processed
- **THEN** `RawEventStatePort.recordError(rawEventId, message)` records the exception message
- **AND** the remaining raw events in the batch are still processed
- **AND** no exception escapes `ProcessRawEventBatchService`

#### Scenario: Rule intents are applied and executed rules recorded

- **GIVEN** enabled rules that produce dedup, threshold, correlation, notify and push intents
- **WHEN** a raw event is processed
- **THEN** the dedup branch merges through `DedupMerger` while a decision without dedup saves the event directly
- **AND** threshold and correlation intents run their evaluators, with correlation able to set `rootEventId`
- **AND** notify intents call `NotificationPort.notify(ruleId, channel, emailAddress)` and push intents call `PushNotificationPort.createPush(ruleId, eventId, title, message)`
- **AND** the rendered push message substitutes `{title}` and `{severity}`, defaulting to the event title and then to `"Событие"`
- **AND** `RuleDefinitionPort.markRun(executedRuleIds, now)` records every rule that contributed an intent
- **AND** the legacy fallback for rules without a canvas still yields default dedup, default threshold, or `CorrelationConfig(2, 10, "title")`

### Requirement: Reverse dependencies on the processing context are removed

`notifications` and `rules` SHALL NOT import `ru.wisla.fm.processing` types. Their externally observable behavior SHALL NOT change.

#### Scenario: Notification stub takes primitives

- **GIVEN** `notifications.api.NotifyStubService.execute(ProcessingDecision.NotifyIntent)`
- **WHEN** the signature is changed to `execute(UUID ruleId, String channel, String emailAddress)`
- **THEN** the import of `ru.wisla.fm.processing.canvas` disappears from `notifications`
- **AND** the method remains a no-op stub with unchanged behavior

#### Scenario: Rule canvas validator uses local view types

- **GIVEN** `rules.api.RuleCanvasValidator` imports `processing.canvas.CanvasNodeView` and `CanvasEdgeView`
- **WHEN** `rules` gains its own local view records
- **THEN** the import of `ru.wisla.fm.processing` disappears from `rules`
- **AND** the REST contract and every validation error message string are unchanged, as proved by `RuleCanvasValidatorTest`

### Requirement: Frozen console and processing contracts remain unchanged

The refactor SHALL NOT change the console REST contracts served from the processing context, and SHALL NOT change the database DDL.

#### Scenario: Event console endpoints are preserved

- **GIVEN** the console event API
- **WHEN** it is called
- **THEN** `GET /api/v1/events`, `GET /api/v1/events/{id}`, `PATCH /api/v1/events/{id}` and `POST /api/v1/events/{id}/actions` keep returning `EventPage`, `EventDetailDto`, `EventDto` and `EventActionResult`
- **AND** the sortable fields `createdAt`, `lastRepeatAt`, `repeatCount`, `severity`, `status`, `title`, `nodeFqdn` and `systemName` remain supported, with the custom severity ordering and the `lastRepeatAt` nulls ordering unchanged
- **AND** `EventControllerTest`, `DashboardControllerTest`, `AdminControllerTest`, `SourceControllerTest`, `ProductHealthControllerTest`, `RuleControllerTest` and the push-notification tests pass with only import or type-name edits

#### Scenario: Out-of-scope consumers are updated by rename only

- **GIVEN** `dashboard/DashboardService`, `admin/AdminService`, `configuration/SourceService`, `health/ProductHealthService`, `config/DevDataSeeder` and the console services inside `processing`, all of which reference `EventEntity`
- **WHEN** `EventEntity` is renamed to `EventJpaEntity`
- **THEN** those classes are updated by type and import rename only, with no logic change
- **AND** they continue to use the JPA repository directly rather than being migrated onto ports, which is recorded as a deliberate deviation in `design.md`

### Requirement: Spring-free use-case tests exist for the processing core

The processing use case and every extracted domain service SHALL have unit tests that run without a Spring application context, using outbound-port test doubles.

#### Scenario: Processing use case is tested with six port fakes

- **GIVEN** the refactored processing context
- **WHEN** `mvn test` runs
- **THEN** `ProcessRawEventBatchServiceTest` constructs `ProcessRawEventBatchService` directly with fakes of `RawEventStatePort`, `EventStorePort`, `CiLookupPort`, `RuleDefinitionPort`, `NotificationPort` and `PushNotificationPort`
- **AND** it is not annotated `@SpringBootTest` and loads no application context

#### Scenario: Extracted domain services are tested directly

- **GIVEN** the extracted domain services
- **WHEN** `mvn test` runs
- **THEN** `RuleGraphTraverserTest`, `RuleCanvasCompilerTest`, `RuleConditionEvaluatorTest`, `SwitchBranchSelectorTest`, `DedupMergerTest`, `ThresholdEvaluatorTest`, `CorrelationEvaluatorTest`, `EventFactoryTest` and `PushMessageRendererTest` all run as plain JUnit 5 tests
- **AND** the traversal tests cover the legacy fallback for `dedup`, `threshold` and `correlation` rules without a canvas, and traversal stopping on a false `condition` node

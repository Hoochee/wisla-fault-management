## MODIFIED Requirements

### Requirement: Processing context follows the ADR-001 package layout

`ru.wisla.fm.processing` SHALL be organized into `domain`, `domain/service`, `application/port/in`, `application/port/out`, `application/service`, `adapter/out/...`, and `infrastructure/config`. `domain` and `application` SHALL be free of Spring, `jakarta.persistence`, Hibernate, Jackson, Kafka and servlet types. The console query/patch island (`api/EventQueryService`, `service/EventUpdateService`) remains unmigrated. `api/EventController` remains in `processing.api` as the HTTP inbound adapter (ADR-001 exception D7) and SHALL invoke inbound ports rather than a layered `@Service` with JPA. Duty actions SHALL go through `PerformEventActionUseCase`; `processing.service.EventActionService` SHALL be removed once that use case owns `take`, `close`, `comment`, `ack`, `assign`, and `silence`.

#### Scenario: Batch processing is reachable through ProcessRawEventBatchUseCase

- **GIVEN** the processing context
- **WHEN** its inbound ports are inspected
- **THEN** `ProcessRawEventBatchUseCase.process(List<UUID> rawEventIds)` is implemented by `application/service/ProcessRawEventBatchService`
- **AND** it is the contract that `ingestion` calls through `ProcessRawEventBatchPort`

#### Scenario: Duty actions are reachable through PerformEventActionUseCase

- **GIVEN** the processing context after event-duty-actions
- **WHEN** its inbound ports are inspected
- **THEN** `PerformEventActionUseCase` is implemented by `application/service/PerformEventActionService`
- **AND** `EventController` in `processing.api` calls that port and does not depend on `EventJpaRepository`
- **AND** `PerformEventActionService` has no Spring, JPA, Jackson, or servlet types
- **AND** `PerformEventActionServiceTest` constructs the service with outbound-port fakes and loads no Spring context

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
- **AND** additive duty-action columns on `events` are mapped on `EventJpaEntity` and `EventJpaMapper`, not on a domain `@Entity`
- **AND** the domain `Event` keeps `attributes` as a JSON `String` so nothing is re-serialized

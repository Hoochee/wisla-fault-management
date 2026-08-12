## ADDED Requirements

### Requirement: Adapter ingest context follows the ADR-001 package layout

`backend/adapter` SHALL organize its ingest bounded context under the root package `com.wisla.fm.adapter.ingest` with the layers `domain`, `application/port/in`, `application/port/out`, `application/service`, `adapter/in`, `adapter/out`, and `infrastructure/config`, as defined by `docs/adr/ADR-001-hexagonal-architecture.md`. No class in `domain` or `application` SHALL import Spring, `jakarta.persistence`, Hibernate, Jackson, Kafka, or `jakarta.servlet` types.

#### Scenario: Ingest use cases are reachable only through inbound ports

- **GIVEN** the refactored `backend/adapter`
- **WHEN** the inbound ports are inspected
- **THEN** `ReceiveWebhookEventUseCase.receive(ReceiveWebhookCommand)` replaces `WebhookService.receive(...)`
- **AND** `DeliverIngestEventUseCase.deliver(DeliverCommand)` replaces the package-private `WebhookService.deliver(...)` used by `ProbeService`
- **AND** `RetryBufferedEventsUseCase.retryDueMessages(Instant)` replaces the body of `BufferRetryWorker.retryBufferedMessages()`
- **AND** `SyncSourceConfigUseCase.sync()` replaces `SourceConfigSyncService.syncFromFmModule()`

#### Scenario: Every outbound side effect has one port and one implementation

- **GIVEN** the use-case services `ReceiveWebhookEventService`, `RetryBufferedEventsService` and `SyncSourceConfigService`
- **WHEN** their dependencies are inspected
- **THEN** they depend only on `domain` types, on `java.time.Clock`, and on the outbound ports `SourceConfigLookupPort`, `SourceConfigStorePort`, `BufferedEventStorePort`, `RawEventPublisherPort`, `ApiKeyVerifierPort` and `FmModuleSourceConfigPort`
- **AND** each of those ports has exactly one implementation under `adapter/out/persistence`, `adapter/out/kafka`, `adapter/out/crypto` or `adapter/out/http`
- **AND** no `PasswordEncoder`, `ObjectMapper`, `RestClient`, repository, or `AdapterProperties` reference remains in `application`

#### Scenario: Transport concerns move into the inbound web adapter

- **GIVEN** a webhook request whose body exceeds `wisla.adapter.max-payload-bytes` or is not valid JSON
- **WHEN** it reaches `adapter/in/web/WebhookController`
- **THEN** `WebhookPayloadReader` enforces the size limit and returns HTTP 413 with error code `payload_too_large`
- **AND** it rejects unparseable bodies with HTTP 400 and error code `invalid_json`
- **AND** neither check is performed inside `ReceiveWebhookEventService`

#### Scenario: Domain rules leave their Spring beans

- **GIVEN** the pre-filter, payload normalization and buffer-backoff logic
- **WHEN** their location is inspected after the refactor
- **THEN** `FilterService` has become behavior on the domain types `FilterRules` and `FilterCondition` with no `@Service` annotation
- **AND** `IngestPayloadMapper` has become `domain/IngestPayloadNormalizer`, receiving `adapterVersion` and a `Clock` as parameters instead of injecting `AdapterProperties` and calling `Instant.now()`
- **AND** the exponential backoff formula `base * 2^min(retryCount - 1, 10)` lives on `domain/BufferedEvent.scheduleRetry(baseSeconds, now)` instead of on a JPA entity

#### Scenario: Scheduling stays in the inbound adapter layer

- **GIVEN** the buffer retry worker and the configuration sync worker
- **WHEN** their annotations are inspected
- **THEN** `@Scheduled` and `ApplicationRunner` appear only on `adapter/in/scheduler/BufferRetryScheduler` and `adapter/in/scheduler/SourceConfigSyncScheduler`
- **AND** the schedule expressions `${wisla.adapter.buffer-retry-interval-ms:60000}` and `${wisla.adapter.config-sync-interval-ms:300000}` are unchanged

### Requirement: fm-module ingestion context follows the ADR-001 package layout

`ru.wisla.fm.ingestion` SHALL be organized into `domain`, `application/port/in`, `application/port/out`, `application/service`, `adapter/in/web`, `adapter/in/messaging`, `adapter/out/persistence`, `adapter/out/processing`, and `infrastructure/config`. `domain` and `application` SHALL be free of Spring, JPA, Hibernate, Jackson, Kafka and servlet types.

#### Scenario: Authentication no longer reaches the use case

- **GIVEN** an authenticated `POST /api/v1/ingest` request
- **WHEN** `adapter/in/web/IngestController` handles it
- **THEN** the controller extracts the `UUID` principal placed by `SourceApiKeyAuthenticationFilter` and builds an `IngestCommand(sourceId, heartbeat, events, adapterVersion, receivedAt)`
- **AND** `IngestEventsUseCase.ingest(IngestCommand)` is the single entry point for both the HTTP and the Kafka path
- **AND** no `org.springframework.security` type appears in `application`

#### Scenario: Raw-event persistence moves behind a port

- **GIVEN** `IngestEventsService` needs to store raw events
- **WHEN** it persists one
- **THEN** it calls `RawEventStorePort.save(RawEvent)`
- **AND** `adapter/out/persistence/RawEventPersistenceAdapter` performs the write through `RawEventJpaRepository` and `RawEventJpaMapper`
- **AND** the Jackson serialization of `attributes` and `rawPayload` into the `raw_events` jsonb columns happens inside the mapper, not in the use case

#### Scenario: accepted and rejected counters keep their current semantics

- **GIVEN** an ingest batch of three events where one fails to serialize or persist
- **WHEN** `IngestEventsService` processes the batch
- **THEN** the response reports `accepted = 2` and `rejected = 1`
- **AND** `rawEventIds` contains only the two stored identifiers
- **AND** all events in the batch share one `ingestBatchId`
- **AND** events without an explicit status are stored with `status = "new"`

#### Scenario: ingestion no longer imports processing directly

- **GIVEN** the refactored `ru.wisla.fm.ingestion`
- **WHEN** its imports are inspected
- **THEN** no class imports `ru.wisla.fm.processing.service.EventProcessingService` or any other `ru.wisla.fm.processing` type from `domain` or `application`
- **AND** the batch hand-off goes through the outbound port `ProcessRawEventBatchPort`, implemented by `adapter/out/processing/ProcessRawEventBatchAdapter`, which calls `processing`'s inbound port
- **AND** the port is invoked only when the list of stored raw-event ids is non-empty

#### Scenario: Kafka listener stops reading the repository itself

- **GIVEN** `adapter/in/messaging/RawEventKafkaListener`
- **WHEN** its constructor dependencies are inspected
- **THEN** it no longer injects `configuration.persistence.EventSourceRepository`
- **AND** the unknown-or-inactive-source decision is obtained through `EventSourceStatePort` or surfaced as `IllegalArgumentException` from the use case
- **AND** logging and the commit/skip policy remain in the adapter

### Requirement: The ingest transaction boundary is unchanged

Moving `@Transactional` off the application service SHALL NOT change the transaction boundary. Ingesting a batch and processing it SHALL remain a single transaction, with identical rollback and Kafka offset-commit behavior. `propagation`, `isolation` and `readOnly` settings SHALL remain at their current values.

#### Scenario: Ingest and processing share one transaction

- **GIVEN** `@Transactional` has moved from `IngestService` onto `IngestController.ingest` and `RawEventKafkaListener.onMessage`
- **WHEN** an ingest request stores raw events and triggers batch processing
- **THEN** both the raw-event writes and the processing writes occur in one transaction
- **AND** an uncaught exception rolls back the whole unit of work
- **AND** `IngestControllerTest`, `RawEventKafkaConsumerTest` and `RawEventKafkaListenerTest` pass unchanged

#### Scenario: A processing error does not roll back the ingest

- **GIVEN** a batch of two raw events where processing the first throws
- **WHEN** the batch is processed inside the ingest transaction
- **THEN** `raw_events.processing_error` is populated for the failing event
- **AND** the transaction still commits, so both raw events and the successfully processed event remain persisted
- **AND** an automated test asserts this explicitly

#### Scenario: Kafka redelivery policy is unchanged

- **GIVEN** a message on `fm.raw-events`
- **WHEN** it is unparseable, has a null `sourceId` or null `body`, refers to an unknown or non-`active` source, or causes `IllegalArgumentException`
- **THEN** the listener logs and skips it, committing the offset
- **AND** any other `RuntimeException` propagates so the offset is not committed and the record is redelivered

### Requirement: Frozen ingest contracts remain byte-identical

The refactor SHALL NOT change any externally observable ingest contract: REST paths, HTTP methods, status codes, error codes, DTO field names, the Kafka topic, key, envelope schema and consumer group, or the database DDL.

#### Scenario: Webhook error codes and statuses are preserved

- **GIVEN** `POST /webhook/{sourceKey}`
- **WHEN** each failure mode is exercised
- **THEN** the responses remain 400 `invalid_json`, 400 `filtered`, 401 `missing_api_key`, 401 `invalid_source_key`, 403 `source_blocked`, 404 `unknown_source`, 413 `payload_too_large` and 502 `ingest_rejected`
- **AND** a successful call returns 202 with `WebhookAcceptedResponse{accepted, delivery, message_id, ingest_status}` under `JsonInclude.NON_NULL`, where `delivery` is `forwarded` or `buffered`
- **AND** both the `X-Source-Key` header and the `sourceKey` query parameter remain accepted
- **AND** `WebhookControllerTest` passes without modification other than imports

#### Scenario: Adapter internal and health endpoints are preserved

- **GIVEN** the adapter's non-webhook endpoints
- **WHEN** they are called
- **THEN** `GET /health` returns `HealthResponse{status, version, database, fm_module…}` with 503 when `database=down` and 200 otherwise
- **AND** `GET /internal/sources/{sourceId}/config` returns `SourceConfigSnapshotDto`, 404 `config_not_found`, or 401 `unauthorized` for a bad `Authorization: Bearer` token
- **AND** `POST /internal/probe` returns `ProbeResponse` including `latency`, `delivery` and `ingest_status`
- **AND** `POST /internal/config/sync` returns 202 with no body
- **AND** `HealthControllerTest` and `InternalControllerTest` pass without modification other than imports

#### Scenario: fm-module ingest endpoints are preserved

- **GIVEN** the fm-module ingest surface
- **WHEN** it is called
- **THEN** `POST /api/v1/ingest` returns 202 with `IngestResponse{accepted, rejected, rawEventIds, heartbeatAck}` and answers a `heartbeat = true` request with `(0, 0, [], true)`
- **AND** `GET /api/v1/raw-events` returns `RawEventPage` with `PageMeta`, sorted `createdAt desc`
- **AND** `GET /api/v1/internal/sources` keeps the contract the adapter reads with the `X-Service-Key` header

#### Scenario: Persistence mapping is unchanged

- **GIVEN** the renamed JPA classes `SourceConfigSnapshotJpaEntity`, `BufferedMessageJpaEntity` and `RawEventJpaEntity`
- **WHEN** their mappings are compared with the originals
- **THEN** table names `source_config_snapshots`, `buffered_messages` and `raw_events` are unchanged
- **AND** every column name, `columnDefinition` and `@JdbcTypeCode(SqlTypes.JSON)` declaration is unchanged, including `filter_rules`, `buffered_messages.payload`, `ingest_api_key`, `raw_events.payload` and `raw_events.raw_payload`
- **AND** the Java field types on those jsonb columns are unchanged (`Map<String, Object>` stays a map, `String` stays a string)
- **AND** `git diff` shows no change under `backend/*/src/main/resources/db/**`

#### Scenario: Upsert and expiry semantics are preserved

- **GIVEN** a source-configuration sync run
- **WHEN** `SourceConfigStorePort.upsert(SourceConfig)` stores a snapshot that already exists
- **THEN** `created_at` is not overwritten, matching the previous `SourceConfigSnapshot.replace(...)` behavior
- **AND** `blocked` is set to `status != "active"`
- **AND** the TTL is set to `now + 86400s`
- **AND** a failure of `FmModuleSourceConfigPort` does not throw out of the use case

### Requirement: Spring-free use-case tests exist for the ingest slice

Every migrated ingest use case SHALL have a unit test that constructs the application service directly with in-memory outbound-port test doubles, without `@SpringBootTest` and without loading a Spring application context.

#### Scenario: Adapter use cases are tested without Spring

- **GIVEN** the refactored `backend/adapter`
- **WHEN** `mvn test` runs
- **THEN** `ReceiveWebhookEventServiceTest`, `RetryBufferedEventsServiceTest` and `SyncSourceConfigServiceTest` execute with plain JUnit 5 and port fakes
- **AND** none of them is annotated `@SpringBootTest` or loads an application context
- **AND** `FilterRulesTest`, `IngestPayloadNormalizerTest` and `BufferedEventTest` cover the extracted domain rules the same way

#### Scenario: fm-module ingestion use cases are tested without Spring

- **GIVEN** the refactored `ru.wisla.fm.ingestion`
- **WHEN** `mvn test` runs
- **THEN** `IngestEventsServiceTest` and `RawEventQueryServiceTest` execute with plain JUnit 5 and fakes of `RawEventStorePort`, `EventSourceStatePort` and `ProcessRawEventBatchPort`
- **AND** neither loads a Spring application context

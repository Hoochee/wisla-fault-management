# architecture-service-independence

## Purpose

Keep `backend/adapter` and `backend/fm-module` as independently deployable microservices with no shared Java types or Maven coupling; integration only over Kafka and HTTP.

## Requirements

### Requirement: No compile-time coupling between the two backend services

`backend/adapter` and `backend/fm-module` are two independent microservices. Neither module SHALL declare a Maven dependency on the other, in either direction. No third Maven module, jar, or source set SHALL be introduced to hold code shared between them. The only dependency added to either `pom.xml` by this change is `com.tngtech.archunit:archunit-junit5` at `test` scope.

#### Scenario: Neither pom depends on the other service

- **GIVEN** the refactor is complete
- **WHEN** `backend/adapter/pom.xml` and `backend/fm-module/pom.xml` are diffed against `origin/main`
- **THEN** the only added dependency in each is `com.tngtech.archunit:archunit-junit5` with `<scope>test</scope>`
- **AND** neither pom contains a dependency on the other service's `groupId`/`artifactId`
- **AND** no new Maven module, aggregator pom, or shared jar has been added anywhere in `backend/`

#### Scenario: Each service builds and tests on its own

- **GIVEN** a clean checkout
- **WHEN** `mvn test` is run in `backend/adapter` without ever building `backend/fm-module`
- **THEN** the build succeeds
- **AND** the same holds for `mvn test` in `backend/fm-module` without building `backend/adapter`

### Requirement: No shared Java types between the two services

The two services SHALL NOT share any Java class, record, interface, enum, or constant holder — including the Kafka envelope, the ingest DTOs, severity and status vocabularies, and topic or header name constants. Each service SHALL keep its own private copy of every type that describes the wire contract. This duplication is a deliberate, documented cost of service independence and SHALL NOT be removed by extracting a shared module.

#### Scenario: No class from one service is imported by the other

- **GIVEN** the refactored source of both services
- **WHEN** every source file under `backend/adapter/src` is inspected for imports
- **THEN** no file imports any type from the `ru.wisla.fm..` package tree
- **AND** no source file under `backend/fm-module/src` imports any type from the `com.wisla.fm.adapter..` package tree

#### Scenario: Each service keeps its own Kafka envelope type

- **GIVEN** the Kafka contract for topic `fm.raw-events`
- **WHEN** the envelope types are located after the refactor
- **THEN** `backend/adapter` owns `com.wisla.fm.adapter.ingest.adapter.out.kafka.RawEventEnvelope` whose `body` field is a `Map<String, Object>`
- **AND** `backend/fm-module` owns `ru.wisla.fm.ingestion.adapter.in.messaging.RawEventEnvelope` whose `body` field is a validated `IngestRequest`
- **AND** both declare `schemaVersion`, `messageId`, `producedAt`, `sourceId`, `sourceKey` and `body` with identical JSON field names
- **AND** neither type is moved into a shared location or made to reference the other

#### Scenario: A shared contracts module is rejected

- **GIVEN** a reviewer or implementer proposes extracting `RawEventEnvelope`, `IngestRequest`, or the severity/status vocabulary into a shared `fm-contracts` module to remove duplication
- **WHEN** the proposal is evaluated against this change
- **THEN** it is rejected, because a shared jar would couple the release cycles of two independently deployable services and convert a tolerant, `schemaVersion`-evolvable wire contract into a rigid compile-time contract
- **AND** the rejection and its rationale are recorded in `design.md`

### Requirement: fm-module is reached only as an external system over the network

Every dependency the adapter has on `fm-module` SHALL be expressed as an outbound port to an external system, implemented in `adapter/out/http` or `adapter/out/kafka` and speaking HTTP or Kafka. Such an adapter SHALL map remote payloads into the adapter service's own domain model and SHALL NOT reference any fm-module Java type. Integration between the services SHALL remain limited to Kafka topic `fm.raw-events` and the internal HTTP endpoints.

#### Scenario: Source-configuration sync crosses the boundary over HTTP

- **GIVEN** `SyncSourceConfigUseCase` needs the source configuration that `fm-module` owns
- **WHEN** `SyncSourceConfigService` obtains it
- **THEN** it calls the outbound port `FmModuleSourceConfigPort`
- **AND** the implementation `FmModuleSourceConfigClient` in `adapter/out/http` issues `GET /api/v1/internal/sources` with the `X-Service-Key` header
- **AND** the client maps the JSON response into the adapter's own `SourceConfig` domain model without importing any `ru.wisla.fm` type

#### Scenario: Event delivery crosses the boundary over Kafka

- **GIVEN** a webhook event accepted by the adapter
- **WHEN** it is delivered to `fm-module`
- **THEN** `RawEventKafkaPublisher` publishes to topic `fm.raw-events` with key `sourceId.toString()` and a `RawEventEnvelope` value carrying `schemaVersion = 1`
- **AND** `fm-module` consumes it through its own envelope copy and its own consumer group
- **AND** no in-process or compile-time call path exists between the two services

### Requirement: Service independence is enforced by ArchUnit

Each service SHALL contain an ArchUnit rule that fails the build when a class in one service's package tree depends on the other service's package tree.

#### Scenario: Adapter rule forbids fm-module imports

- **GIVEN** `HexagonalArchitectureTest` in `backend/adapter`
- **WHEN** `mvn test` runs
- **THEN** a rule asserts that no class residing in `com.wisla.fm.adapter..` depends on classes residing in `ru.wisla.fm..`
- **AND** the rule fails the build if such a dependency is introduced

#### Scenario: fm-module rule forbids adapter imports

- **GIVEN** `HexagonalArchitectureTest` in `backend/fm-module`
- **WHEN** `mvn test` runs
- **THEN** a rule asserts that no class residing in `ru.wisla.fm..` depends on classes residing in `com.wisla.fm.adapter..`
- **AND** the rule fails the build if such a dependency is introduced

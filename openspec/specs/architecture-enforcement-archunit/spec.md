# architecture-enforcement-archunit

## Purpose

Automated ArchUnit enforcement of hexagonal layering and service boundaries for the adapter and fm-module pilot contexts, with Java 25 compatibility and ADR outcome recording.

## Requirements

### Requirement: ArchUnit is added to both backend services at test scope

`backend/adapter/pom.xml` and `backend/fm-module/pom.xml` SHALL each declare `com.tngtech.archunit:archunit-junit5` with `<scope>test</scope>`, and each module SHALL contain a `HexagonalArchitectureTest` executed by `mvn test`. No other dependency SHALL be added by this change, and neither MapStruct nor Testcontainers SHALL be introduced.

#### Scenario: Only the ArchUnit dependency is added

- **GIVEN** the change is complete
- **WHEN** the pom diffs are reviewed and `mvn dependency:list` is run for each module
- **THEN** the only added dependency in each module is `com.tngtech.archunit:archunit-junit5` at `test` scope
- **AND** no MapStruct artifact or annotation processor is present
- **AND** no Testcontainers artifact is present

#### Scenario: Architecture tests run as part of the build

- **GIVEN** `backend/adapter/src/test/java/com/wisla/fm/adapter/architecture/HexagonalArchitectureTest.java` and `backend/fm-module/src/test/java/ru/wisla/fm/architecture/HexagonalArchitectureTest.java`
- **WHEN** `mvn test` runs in each module
- **THEN** both test classes execute and pass
- **AND** introducing a layering violation makes the build fail

### Requirement: ArchUnit compatibility with Java 25 is verified before any code moves

Because ArchUnit reads bytecode through ASM and both modules build with `java.version = 25` (class file major version 69), compatibility SHALL be verified as the first task of the change, before any production class is moved. If no ArchUnit release can read the produced bytecode, ArchUnit SHALL be deferred to a follow-up change and the situation escalated at the gate. The production `java.version` SHALL NOT be downgraded, and Phase 2 SHALL NOT be blocked by this outcome.

#### Scenario: Compatibility check is the first step

- **GIVEN** an unmodified working tree
- **WHEN** the change begins
- **THEN** the first action is to add `archunit-junit5` to both poms and run a single trivial rule over each module's own package tree
- **AND** no production class has been moved or renamed at that point

#### Scenario: Bytecode is readable

- **GIVEN** the trivial ArchUnit rule
- **WHEN** it runs against class files compiled with `java.version = 25`
- **THEN** ArchUnit imports the classes without a bytecode-version error
- **AND** the refactor proceeds with rules 1 through 8 as planned

#### Scenario: Bytecode is not readable

- **GIVEN** ArchUnit fails to import Java 25 class files even on the newest available release
- **WHEN** the fallback is applied
- **THEN** the ArchUnit dependency and tests are removed from this change and moved to a documented follow-up change
- **AND** the outcome is escalated at the gate, and ADR conformance for this change is verified by code review against `design.md`
- **AND** `java.version` remains 25 in both poms
- **AND** the rest of the refactor, including the processing phase, still completes

### Requirement: ArchUnit enforces the ADR-001 layering rules

Each `HexagonalArchitectureTest` SHALL assert the dependency rules of `docs/adr/ADR-001-hexagonal-architecture.md` for its module's in-scope packages.

#### Scenario: Domain is free of framework types

- **GIVEN** classes residing in `..domain..` within an in-scope context
- **WHEN** the architecture test runs
- **THEN** none of them depends on `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `com.fasterxml.jackson..`, `org.apache.kafka..`, `org.springframework.kafka..` or `jakarta.servlet..`
- **AND** none of them depends on `..application..` or `..adapter..`

#### Scenario: Application depends only on domain and ports

- **GIVEN** classes residing in `..application..` within an in-scope context
- **WHEN** the architecture test runs
- **THEN** none of them depends on the framework packages listed above
- **AND** none of them depends on `..adapter..` or `..infrastructure..`

#### Scenario: Persistence types are confined to the outbound persistence adapter

- **GIVEN** the in-scope packages
- **WHEN** the architecture test runs
- **THEN** every class annotated `@Entity` or `@Table` resides in `..adapter.out.persistence..`
- **AND** every interface assignable to `org.springframework.data.repository.Repository` resides in `..adapter.out.persistence..`

#### Scenario: Transport annotations are confined to inbound adapters

- **GIVEN** the in-scope packages
- **WHEN** the architecture test runs
- **THEN** every class annotated `@RestController`, `@KafkaListener` or `@Scheduled` resides in `..adapter.in..`

#### Scenario: Layered architecture rule holds per context

- **GIVEN** a `layeredArchitecture()` rule for each in-scope context
- **WHEN** it runs
- **THEN** it asserts `domain ← application ← adapter`, with `infrastructure` permitted to depend on all layers

### Requirement: fm-module architecture rules are scoped to the migrated contexts only

Because ten bounded contexts in `backend/fm-module` are deliberately not migrated, every rule in the fm-module architecture test SHALL be restricted to `ru.wisla.fm.ingestion..` and `ru.wisla.fm.processing..`. No rule SHALL be applied module-wide, except the service-independence rule which forbids importing the other service.

#### Scenario: Unmigrated contexts do not fail the build

- **GIVEN** `identity`, `console`, `dashboard`, `admin`, `settings`, `health`, `configuration`, `cmdb`, `rules` and `notifications` still use the layered Spring/JPA structure
- **WHEN** the fm-module architecture test runs
- **THEN** the build passes, because every layering rule is scoped with `resideInAnyPackage("ru.wisla.fm.ingestion..", "ru.wisla.fm.processing..")`
- **AND** no suppression, ignore list, or `@ArchIgnore` is needed for those contexts

#### Scenario: Console classes inside processing are not falsely flagged

- **GIVEN** the out-of-scope console classes `processing/api/EventController`, `processing/api/EventQueryService`, `processing/service/EventActionService` and `processing/service/EventUpdateService`, which remain outside the hexagonal layout
- **WHEN** the architecture test runs
- **THEN** they do not violate any rule, because they reside in neither `..domain..`, `..application..` nor a layer that the rules constrain, and the `@Entity`/repository rules are satisfied once the JPA classes have moved to `..adapter.out.persistence..`

### Requirement: The full build is green in both services after the refactor

`mvn test` SHALL pass in both `backend/adapter` and `backend/fm-module`. No existing test SHALL be deleted or weakened; only import and type-name edits are permitted in existing tests.

#### Scenario: Both modules build green

- **GIVEN** the completed refactor
- **WHEN** `mvn test` runs in `backend/adapter` and then in `backend/fm-module`
- **THEN** both builds succeed
- **AND** the pre-existing regression tests `WebhookControllerTest`, `InternalControllerTest`, `HealthControllerTest`, `BufferRetryWorkerTest`, `RawEventKafkaPublisherTest`, `RawEventEnvelopeCodecTest`, `IngestControllerTest`, `IngestServiceTest`, `RawEventKafkaConsumerTest`, `RawEventKafkaListenerTest`, `RawEventEnvelopeTest`, `EventControllerTest`, `RuleCanvasRuntimeIntegrationTest`, the `RuleCanvas*` tests, `CorrelationServiceTest`, the push-notification tests, `DashboardControllerTest`, `SourceControllerTest`, `AdminControllerTest`, `ProductHealthControllerTest` and `RuleControllerTest` all still pass
- **AND** no test has been removed, disabled, or had an assertion relaxed

#### Scenario: Out-of-scope paths are untouched

- **GIVEN** the completed refactor
- **WHEN** `git diff` against the base branch is inspected
- **THEN** it contains no change under `backend/*/src/main/resources/db/**`, `docs/**/api.yaml`, `frontend/**`, `prototype/**`, `backend/zabbix-simulator/**`, `backend/docker-compose*.yaml` or `backend/docker/**`

### Requirement: ADR-001 records the pilot outcome

`docs/adr/ADR-001-hexagonal-architecture.md` currently lists automated architecture enforcement and moving existing production packages among its non-goals. Once this pilot lands, the ADR SHALL be amended so it no longer contradicts the enforced reality.

#### Scenario: ADR is amended after the refactor

- **GIVEN** the refactor is complete and ArchUnit is enforcing the layering
- **WHEN** `docs/adr/ADR-001-hexagonal-architecture.md` is updated
- **THEN** it records that `com.wisla.fm.adapter.ingest`, `ru.wisla.fm.ingestion` and `ru.wisla.fm.processing` were migrated as the pilot
- **AND** it supersedes its "ArchUnit or other automated architecture enforcement" and "Moving or renaming existing production packages" non-goals for those contexts
- **AND** it continues to list a shared domain JAR between deployables as a non-goal

# architecture-hexagonal

## Purpose

Repository convention for hexagonal backend design: ADR, layer responsibilities, design checklist, and Spring-free use-case test planning, adopted via documentation and workflow without rewriting all production packages.

## Requirements

### Requirement: Hexagonal architecture decision record
The repository SHALL contain an ADR that defines the hexagonal architecture convention for future backend bounded-context changes. The ADR MUST define permitted dependency directions, layer responsibilities, and a target package structure containing `domain`, `application/port/in`, `application/port/out`, `adapter/in`, `adapter/out`, and `infrastructure/config`.

#### Scenario: Architect plans a backend change
- **GIVEN** a proposed change includes backend behavior in a WISLA bounded context
- **WHEN** the architect prepares its design
- **THEN** the design can reference the ADR to determine package ownership and permitted dependency directions

### Requirement: Framework-independent domain and application layers
Future backend designs SHALL require domain code to remain independent of Spring, JPA, Jackson, Kafka, and HTTP types. Application use cases SHALL depend only on domain code and inbound and outbound port abstractions; concrete technical integrations SHALL be implemented by adapters and wired in infrastructure configuration.

#### Scenario: Design identifies a persistence side effect
- **GIVEN** a use case needs to persist domain state
- **WHEN** the design describes the interaction
- **THEN** it defines an outbound port in `application/port/out`, a concrete persistence adapter in `adapter/out`, and framework wiring in `infrastructure/config`

### Requirement: Hexagonal backend design checklist
Every future backend design that introduces or changes behavior SHALL document the use case and inbound port, inbound adapter, outbound ports, concrete outbound adapter implementations, infrastructure wiring, and Spring-free use-case tests.

#### Scenario: Backend design is reviewed
- **GIVEN** a change affects backend behavior
- **WHEN** its design is submitted for review
- **THEN** the reviewer can identify all six checklist elements or an explicitly documented reason that an element is not applicable

### Requirement: Spring-free use-case test planning
Every future backend design that changes a use case SHALL include a unit-test task that constructs the use-case implementation with test doubles for its outbound ports and runs without a Spring application context.

#### Scenario: Backend engineer implements a use case
- **GIVEN** an approved task changes a backend use case
- **WHEN** the backend engineer follows the task plan
- **THEN** the engineer writes and executes a use-case unit test without Spring before or alongside adapter integration tests

### Requirement: Process-only adoption
The architecture convention SHALL be adopted through documentation and workflow guidance without changing existing production packages, runtime behavior, API contracts, database schemas, Docker configuration, or frontend behavior.

#### Scenario: Documentation change is verified
- **GIVEN** the hexagonal architecture change is complete
- **WHEN** its scope is reviewed
- **THEN** changed files are limited to `docs/`, `.agents/`, `build-feature/`, and `openspec/` apart from OpenSpec metadata

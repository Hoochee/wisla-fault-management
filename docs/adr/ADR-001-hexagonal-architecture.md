# ADR-001: Hexagonal Architecture for Backend Bounded Contexts

## Status

Accepted. Amended on 2026-08-03 by change `hexagonal-refactor` — see [Pilot outcome](#pilot-outcome-amendment-2026-08-03).

## Context

WISLA defines deployable services and bounded contexts in [architecture.md](../architecture.md), but did not previously define dependency directions inside a backend bounded context. Without that convention, business decisions can become coupled to Spring, persistence, HTTP, messaging, or serialization details, which makes use cases harder to test and evolve.

This decision governs new or materially changed backend behavior in the `adapter`, `fm-module`, and `zabbix-simulator` services. It does not reorganize the existing implementation — true as originally accepted, and superseded on 2026-08-03 for the three contexts named in [Pilot outcome](#pilot-outcome-amendment-2026-08-03).

## Decision

Each backend bounded context follows the dependency rule below:

- `domain` MUST NOT import Spring, JPA, Jackson, Kafka, or HTTP types.
- `application` implements use cases and depends only on `domain` and inbound or outbound port abstractions.
- `adapter` code translates technology-specific input and implements technology-specific outbound ports.
- Spring bean wiring and framework configuration belong only in `infrastructure/config`.

The target package structure for each bounded context is:

```text
<context>/
  domain/
  application/port/in/
  application/port/out/
  application/service/
  adapter/in/...
  adapter/out/...
  infrastructure/config/
```

Layer responsibilities:

| Layer | Responsibility |
| --- | --- |
| `domain` | Domain models, value objects, domain services, and domain events. |
| `application/port/in` | Use-case contracts invoked by primary adapters. |
| `application/port/out` | Technology-neutral dependencies required by use cases. |
| `application/service` | Use-case implementations and orchestration of domain behavior through ports. |
| `adapter/in` | REST, messaging, scheduling, CLI, or other input translation into inbound-port calls. |
| `adapter/out` | Persistence, HTTP, messaging, and other technology-specific implementations of outbound ports. |
| `infrastructure/config` | Spring wiring and framework configuration. |

For every future backend design that introduces or changes behavior, `design.md` documents:

1. use cases and inbound ports;
2. inbound adapters;
3. outbound ports;
4. outbound adapter implementations;
5. infrastructure wiring; and
6. use-case unit tests without Spring.

Use-case tests instantiate application services with outbound-port test doubles and do not load a Spring application context.

## Consequences

- Backend behavior can be tested independently of Spring and external integrations.
- Technology dependencies remain at adapter and infrastructure edges.
- Each bounded context retains its own domain model and ownership.
- New designs and reviews must explicitly map ports, adapters, and configuration.
- The convention applies when scenarios are new or materially touched; it does not require a mass migration of existing packages.

This convention is consistent with the current integration topology: the adapter communicates with `fm-module` through the existing ingest boundary (synchronous HTTP in MVP and Kafka in the production target control/data-plane design), and the Angular SPA communicates with `fm-module` through its REST/BFF boundary. Those integrations are adapters at their respective edges; this ADR changes no runtime contract.

## Pilot outcome (amendment, 2026-08-03)

Change `hexagonal-refactor` applied this ADR to production code for the first time. Three bounded contexts are migrated and are now the reference implementation of the convention:

- `com.wisla.fm.adapter.ingest` (`backend/adapter`)
- `ru.wisla.fm.ingestion` (`backend/fm-module`)
- `ru.wisla.fm.processing` (`backend/fm-module`)

Each of them now has this structure, where `domain/service` holds the framework-free domain services (rule traversal, dedup merge, threshold and correlation evaluation, event creation, push-message rendering):

```text
<context>/
  domain/
  domain/service/
  application/port/in/
  application/port/out/
  application/service/
  adapter/in/...
  adapter/out/...
  infrastructure/config/
```

The pilot changed no REST contract, no Kafka topic or payload, no database schema, and no Liquibase changelog. After it, `backend/adapter` runs 174 tests and `backend/fm-module` 321, both suites green.

The contexts `identity`, `console`, `dashboard`, `admin`, `settings`, `health`, `configuration`, `cmdb`, `rules`, and `notifications` are not migrated and keep their current layout; they were touched only by mechanical renames. For them the original rule stands unchanged: the convention applies when a scenario is new or materially touched, and no mass migration is required.

### Enforcement

`com.tngtech.archunit:archunit-junit5:1.4.2` is declared at `test` scope in both `backend/adapter/pom.xml` and `backend/fm-module/pom.xml`, and reads Java 25 bytecode (class file major version 69) without issue. Each module has a `HexagonalArchitectureTest`: 9 rules in `backend/adapter`, 10 in `backend/fm-module`, the extra one being the layered rule for `processing`. Every layering rule is scoped to the migrated contexts, so the unmigrated ones cannot fail the build, and no rule is suppressed by `@ArchIgnore` or an exclusion list.

Two limitations are deliberate and recorded rather than hidden:

- The rule confining `@RestController`, `@KafkaListener`, and `@Scheduled` to `..adapter.in..` is scoped to `ru.wisla.fm.ingestion` only. The pilot leaves `processing/api/EventController` and the console services on the JPA repository, outside `adapter/in`; widening the rule to `processing` requires moving the console REST surface, which is a separate decision.
- The layered rule permits `adapter → infrastructure`, because adapters read `@ConfigurationProperties` records that live in `infrastructure/config`. `domain` and `application` remain cut off from `infrastructure`, which is what the dependency rule actually protects.

### Service independence

`backend/adapter` and `backend/fm-module` remain compile-time independent: neither pom depends on the other, no shared module or jar exists, and no Java class is shared between them. Each service owns a private copy of the wire-contract types. The two `RawEventEnvelope` copies are deliberately separate and structurally different — the adapter's `body` is `Map<String, Object>`, fm-module's is a validated `IngestRequest` — while both keep the same JSON field names and `schemaVersion = 1`. Merging them would turn a tolerant, versioned wire contract into a rigid compile-time one and couple the release cycles of two independently deployable services.

Rule 8 of `HexagonalArchitectureTest` enforces this in both modules: no class in `com.wisla.fm.adapter..` may depend on `ru.wisla.fm..`, and no class in `ru.wisla.fm..` may depend on `com.wisla.fm.adapter..`. That is why "A shared domain JAR between deployables" remains a standing non-goal below.

## Alternatives considered

### Document only in agent prompts

Rejected. The convention must be discoverable and reviewable independently of a particular AI workflow.

### One service-wide domain and adapter layer

Rejected. It weakens bounded-context boundaries and prematurely creates a shared model.

### Permit framework annotations in application code

Rejected. It couples use cases to technical adapters and makes unit tests require Spring.

### Add ArchUnit enforcement now

Rejected. The current change is process-only and does not migrate existing code; enforcement can be considered separately after an adoption pilot.

Superseded on 2026-08-03: the pilot happened, and ArchUnit now enforces the layering in both modules for the three migrated contexts.

## Non-goals

- ArchUnit or other automated architecture enforcement — superseded on 2026-08-03 for `com.wisla.fm.adapter.ingest`, `ru.wisla.fm.ingestion`, and `ru.wisla.fm.processing`, where ArchUnit now enforces the layering; still a non-goal for every other context.
- A shared domain JAR between deployables — standing non-goal, not superseded; see [Service independence](#service-independence).
- Splitting services or creating new deployable services.
- Rewriting Kafka or the existing ingest pipeline.
- Moving or renaming existing production packages, entities, controllers, or integrations — superseded on 2026-08-03 for the same three contexts, which were reorganized into the structure above; still a non-goal elsewhere. The rename `EventEntity` → `EventJpaEntity` reached out-of-scope consumers, but only as a logic-free rename.
- Changing REST/OpenAPI contracts, Liquibase changelogs, Docker configuration, frontend behavior, or database schemas.

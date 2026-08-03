# ADR-001: Hexagonal Architecture for Backend Bounded Contexts

## Status

Accepted

## Context

WISLA defines deployable services and bounded contexts in [architecture.md](../architecture.md), but did not previously define dependency directions inside a backend bounded context. Without that convention, business decisions can become coupled to Spring, persistence, HTTP, messaging, or serialization details, which makes use cases harder to test and evolve.

This decision governs new or materially changed backend behavior in the `adapter`, `fm-module`, and `zabbix-simulator` services. It does not reorganize the existing implementation.

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

## Alternatives considered

### Document only in agent prompts

Rejected. The convention must be discoverable and reviewable independently of a particular AI workflow.

### One service-wide domain and adapter layer

Rejected. It weakens bounded-context boundaries and prematurely creates a shared model.

### Permit framework annotations in application code

Rejected. It couples use cases to technical adapters and makes unit tests require Spring.

### Add ArchUnit enforcement now

Rejected. The current change is process-only and does not migrate existing code; enforcement can be considered separately after an adoption pilot.

## Non-goals

- ArchUnit or other automated architecture enforcement.
- A shared domain JAR between deployables.
- Splitting services or creating new deployable services.
- Rewriting Kafka or the existing ingest pipeline.
- Moving or renaming existing production packages, entities, controllers, or integrations.
- Changing REST/OpenAPI contracts, Liquibase changelogs, Docker configuration, frontend behavior, or database schemas.

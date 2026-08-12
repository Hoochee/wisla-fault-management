## Context

WISLA already defines deployable services and bounded contexts in `docs/architecture.md`, but it does not define how a backend bounded context separates business rules from framework and integration code. This documentation and process change applies to future work in the `adapter`, `fm-module`, and `zabbix-simulator` backend contexts; it does not reorganize their existing packages.

The approved scope is limited to `docs/`, `.agents/`, `build-feature/`, and `openspec/`. Architecture instructions must stay consistent with the current integration topology: Angular SPA → fm-module REST and adapter → fm-module ingest. No runtime contracts are changed.

## Goals / Non-Goals

**Goals:**

- Establish ADR-001 as the normative source for hexagonal dependency directions.
- Document one target package layout for each backend bounded context: `domain`, `application/port/in`, `application/port/out`, `adapter/in`, `adapter/out`, and `infrastructure/config`.
- Make future architecture proposals identify use cases, inbound adapters, outbound ports, concrete outbound adapters, configuration wiring, and Spring-free use-case tests.
- Make backend implementation and review instructions enforce the same boundaries without introducing a runtime enforcement tool.
- Add a process-level OpenSpec capability with testable Given/When/Then scenarios.

**Non-Goals:**

- Moving or renaming existing production packages, entities, controllers, or integrations.
- Changing ingestion behavior, Kafka topology, REST/OpenAPI contracts, Liquibase changelogs, Docker Compose, frontend routes, or the prototype.
- Creating shared domain modules across deployables or new deployable services.
- Adding ArchUnit or another automatic architecture-enforcement dependency.

## Decisions

### Record the convention in an ADR and planning artifacts

`docs/adr/ADR-001-hexagonal-architecture.md` will be the stable decision record. Agent instructions, the build-feature workflow, and OpenSpec design checklist will reference the ADR rather than duplicating competing rules.

Alternative considered: document the convention only in agent prompts. Rejected because the rule must remain discoverable and reviewable independently of a specific AI workflow.

### Use a bounded-context-local target package structure

Each bounded context in a backend service will use the following target structure when it is touched by future feature work:

```text
<bounded-context>/
  domain/
  application/
    port/
      in/
      out/
  adapter/
    in/
    out/
  infrastructure/
    config/
```

`domain` contains domain models, value objects, domain services, and domain events and MUST NOT depend on Spring, JPA, Jackson, Kafka, or HTTP types. `application` implements use cases and depends only on domain code and port abstractions. `adapter/in` translates REST, messaging, scheduling, or CLI inputs into inbound-port calls. `adapter/out` implements outbound ports for persistence, HTTP, messaging, or other technologies. `infrastructure/config` owns Spring bean wiring and framework configuration.

Alternative considered: a single service-wide `domain` and adapter layer. Rejected because it weakens existing bounded-context boundaries and creates a premature shared model.

### Mandate a six-part design checklist for future backend work

For every future backend design that creates or changes behavior, `design.md` MUST document:

1. the use case and its inbound port;
2. inbound adapter(s) and the transport they translate;
3. outbound port(s) needed by the use case;
4. the concrete outbound adapter implementation for every outbound port;
5. infrastructure/configuration wiring; and
6. use-case tests that instantiate the application service without Spring.

The checklist is documentation and review guidance for this change. It becomes code only when a later feature adopts it.

Alternative considered: require only package names. Rejected because package names alone do not prove dependency direction, adapter mapping, or framework-independent testing.

### Keep framework ownership at the edge

Spring configuration, controller annotations, JPA repositories/entities, HTTP clients, Kafka listeners/producers, and serialization DTOs remain adapter or infrastructure concerns. Application ports express required behavior in domain-oriented terms; no Spring, persistence, or transport API leaks through domain or application interfaces.

Alternative considered: permit framework annotations in application code for convenience. Rejected because it makes unit tests require Spring and couples use-case evolution to technical adapters.

### Review architecture from changed boundaries, without ArchUnit

The code reviewer will check changed backend code against the ADR, design checklist, tests, and package ownership. The reviewer will flag framework leakage into domain/application, missing ports for side effects, adapters containing use-case decisions, absent Spring-free use-case tests, or undocumented deviations.

Alternative considered: add ArchUnit now. Rejected because the approved scope explicitly excludes automatic enforcement and existing code is not being migrated.

## Module changes

### docs/

Add ADR-001 with dependency rules, layer responsibilities, target package structure, adoption expectations, and explicit non-goals. Link the convention to existing bounded contexts documented in `docs/architecture.md` without revising runtime architecture.

### .agents/

Update architect guidance to require the six-part design checklist and explicit dependency-direction decisions. Update backend-engineer guidance to place new code by responsibility and create unit tests that construct use cases without Spring. Update reviewer guidance to review layer boundaries, port/adaptor mapping, and framework-free use-case tests.

### build-feature/

Update `build-feature/SKILL.core.md` so design and review phases verify the hexagonal checklist for backend scope while retaining existing gates and TDD flow.

### openspec/

Update the design-artifact template or checklist guidance so future backend `design.md` files include the six required elements. Add the `architecture-hexagonal` delta spec for the documentation/process convention.

### Runtime modules

`backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, `frontend/`, and `prototype/` have no code, API, schema, Docker, or UI changes in this proposal. Their existing adapter → fm-module ingest and SPA → fm-module REST integration points remain unchanged.

## Risks / Trade-offs

- [The convention may be applied inconsistently before existing code is migrated] → Treat it as mandatory for newly designed or materially changed backend behavior and record deviations in the design.
- [Package structure may be over-applied to trivial changes] → Require only the relevant checklist items and avoid artificial abstractions where no boundary or side effect exists.
- [Manual review can miss violations] → Use explicit agent and OpenSpec checklists now; reconsider ArchUnit only in a separately approved enforcement change.
- [Documentation can diverge across sources] → ADR-001 is the normative decision, and workflow documents reference it.

## Migration Plan

1. Add the ADR and target package-structure documentation.
2. Update architect, backend-engineer, reviewer, and build-feature instructions.
3. Add the OpenSpec design checklist and capability spec.
4. Verify all documents reference the same dependency directions and scope boundaries.

No deployment, database migration, rollout, or runtime rollback is required. Reverting the documentation/process files restores the prior convention without affecting deployed services.

## Open Questions

- Which future backend change should be the first intentional pilot for the convention?
- Should a later, separately scoped change introduce ArchUnit after the pilot establishes stable package boundaries?

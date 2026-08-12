## Why

WISLA documents bounded contexts and deployable services but does not prescribe dependency directions inside backend contexts. As a result, future work can couple domain decisions to Spring, persistence, HTTP, or messaging details, making use cases harder to test and evolve.

## What Changes

- Add an ADR that establishes hexagonal dependency directions and responsibilities for future backend changes.
- Document a consistent target package structure for every bounded context.
- Require the architect, backend engineer, code reviewer, `/build-feature`, and OpenSpec design guidance to apply and review the hexagonal checklist.
- Define the checklist: use cases, inbound adapters, outbound ports, outbound adapter implementations, infrastructure wiring, and Spring-free use-case tests.
- Preserve the existing runtime implementation; this change creates documentation and process conventions only.

## Capabilities

### New Capabilities
- `architecture-hexagonal`: Repository architecture convention requiring documented hexagonal boundaries and testable use cases for future backend designs.

### Modified Capabilities

None.

## Impact

Affected modules are `docs/`, `.agents/`, `build-feature/`, and `openspec/`. The change adds `docs/adr/ADR-001-hexagonal-architecture.md` and updates planning and review guidance; it does not change `backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, `frontend/`, or `prototype/`.

There are no Liquibase SQL, REST/OpenAPI, Docker Compose, Angular route, API, or runtime dependency changes. Non-goals include migrating existing packages or production logic, adding ArchUnit enforcement, rewriting Kafka/ingest, or changing UI and database schemas.

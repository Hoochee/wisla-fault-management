## 1. ADR and architecture documentation

- [x] 1.1 Draft `docs/adr/ADR-001-hexagonal-architecture.md` with the dependency rule, layer responsibilities, target package structure, adoption guidance, alternatives, and explicit non-goals.
- [x] 1.2 Document the target `domain`, `application/port/in`, `application/port/out`, `adapter/in`, `adapter/out`, and `infrastructure/config` structure for each future backend bounded context without moving existing packages.
- [x] 1.3 Verify the ADR is consistent with `docs/architecture.md` bounded contexts and preserves the adapter → fm-module ingest and SPA → fm-module REST integration boundaries.

## 2. Agent workflow guidance

- [x] 2.1 Update `.agents/05-architect.md` to require the six-part hexagonal design checklist and dependency-direction decisions for future backend changes.
- [x] 2.2 Update `.agents/07-backend-engineer.md` to place new code by layer responsibility and require Spring-free use-case tests with outbound-port test doubles.
- [x] 2.3 Update `.agents/09-code-reviewer.md` to assess domain/application framework independence, port-to-adapter mapping, infrastructure wiring, and use-case tests without Spring.
- [x] 2.4 Verify the three agent documents consistently reference ADR-001 and do not require a mass refactor or ArchUnit.

## 3. Build-feature workflow

- [x] 3.1 Update `build-feature/SKILL.core.md` so backend design and review phases verify the hexagonal checklist while retaining existing gates, TDD flow, and module boundaries.
- [x] 3.2 Verify the build-feature workflow applies the checklist only to future backend behavior changes and does not introduce frontend, Docker, API, or schema work.

## 4. OpenSpec design checklist

- [x] 4.1 Update `openspec/config.yaml` design guidance to require use cases, inbound adapters, outbound ports, outbound adapter implementations, infrastructure wiring, and Spring-free use-case test planning for backend scope.
- [x] 4.2 Verify `openspec/changes/hexagonal-architecture/specs/architecture-hexagonal/spec.md` covers the ADR, layer boundaries, checklist, Spring-free tests, and process-only scope with Given/When/Then scenarios.

## 5. Consistency verification

- [x] 5.1 Review the changed docs, agent instructions, build-feature guidance, and OpenSpec configuration against ADR-001; confirm all dependency directions and terminology match.
- [x] 5.2 Confirm no files under `backend/`, `frontend/`, `prototype/`, Docker configuration, Liquibase changelogs, or OpenAPI contracts were changed.

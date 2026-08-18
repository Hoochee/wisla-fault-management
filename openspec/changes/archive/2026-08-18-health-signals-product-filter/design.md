## Context

Operators on `/health` and `/health/:productId` click «Перейти к сигналам» and land on `/console?ci=<ciId>`. `ConsolePageComponent` already reads `severity` (FM-19 `applySeverityQueryParam`) but ignores `ci`. The table loads unfiltered.

`docs/pages-spec.md` already specifies a product-card header link **В консоль** with a product prefilter; that link is missing. `docs/fm-module/api.yaml` already documents `GET /api/v1/events?productId=` (UUID). `EventController.listEvents` today binds `status`, `severity`, `sourceId`, `ciId`, `includeSilenced`, `sort`, `page`, `size` — **not** `productId`. `EventQueryService.buildSpec` equality-filters a single `ciId` when present.

Product ↔ CI membership already lives in `product_ci`, queried by `ProductCiRepository.findCiIdsByProductId`. Health calculation, graph, and snapshot are out of scope.

`EventQueryService` is a Spring `@Service` in `ru.wisla.fm.processing.api`. `openspec/specs/architecture-hexagonal-processing/spec.md` explicitly leaves the console REST surface (`api/EventController`, `api/EventQueryService`) out of the hexagonal processing layout. This change fits the filter into that existing surface; it does not extract a new list-events use case or a new microservice.

Integration chain: Angular SPA → fm-module REST (`GET /api/v1/events`). Adapter → ingest is not in this change.

## Goals / Non-Goals

**Goals:**

- Health: «Перейти к сигналам» in `CiHealthTabComponent` navigates to `/console?productId=<current product uuid>` (not `ci=`). `/health/:productId` header adds «В консоль» with the same param. Operative-center «Открыть в консоли →» / «Консоль →» navigate to `/console?ciId=<selected CI uuid>`.
- Console: valid UUID `productId` query param → removable query-bar chip **before** the first `listEvents` / poll; empty / non-UUID → no chip; clearing the chip → later `listEvents` without `productId`; AND with existing `?severity=`.
- Console: valid UUID `ciId` query param → removable chip via `applyCiIdQueryParam` **before** the first `listEvents` / poll; empty / non-UUID → no chip; clearing the chip → later `listEvents` without `ciId`; AND with `severity` and `productId` if present. Legacy `ci` is ignored.
- `QueryField` includes `productId` and `ciId`; `buildEventApiParams` sends each to `FmApiService.listEvents`. Neither field is in the add-form.
- Backend: `ciId ∈` CIs of that product via `ProductCiRepository.findCiIdsByProductId`. Unknown UUID or empty CI set → empty page, HTTP 200, not 500.
- TDD: backend `mvn test`, frontend Vitest analog of `console-severity-query.test.ts`, Playwright e2e.

**Non-Goals:**

- FM-19 dashboard severity counters / changing `?severity=` behavior.
- `mapId` and legacy `ci` stay ignored on the console (do not also read `ci`).
- Health formula, graph, snapshot, heatmap, weights, product CRUD.
- New top-level services, hexagonal refactor of `EventQueryService`, Liquibase, OpenAPI edits, Docker, adapter, simulator, prototype.

## Decisions

### D1. Filter by the product’s CI set, URL param `productId`

Approved scope: console shows events whose `ciId` belongs to CIs of that product (`ciId ∈ product CIs`). The SPA sends **`productId`** (OpenAPI name). The backend resolves product → CI ids. Product-scoped health links («Перейти к сигналам», header «В консоль») do **not** send the selected tree CI.

Operative-center «Открыть в консоли →» / «Консоль →» **are in scope**: they send **`ciId`** (OpenAPI name for `GET /api/v1/events?ciId=`), not legacy `ci`. Console honors `ciId` via `applyCiIdQueryParam`.

```text
SPA ──REST JWT──► fm-module :8080
  GET /api/v1/events?productId=<uuid>[&severity=<key>]
```

`productId` + `severity` are AND predicates in `buildSpec`. Existing single-`ciId` request param is used by the operative-center CI-scoped links. If a client sends both `productId` and `ciId`, both predicates AND: the event must have that `ciId` **and** that `ciId` must be in the product’s CI set. Events with `ciId == null` never match a product filter.

`EventQueryService.listEvents` is called only from `EventController`. `ProductHealthFacade` uses `toDto`, not `listEvents` — do not change the health snapshot event list. Add `productId` after `ciId` on the controller and service signatures.

Alternative considered: client-only filter after an unfiltered `listEvents`. Rejected — OpenAPI already defines server-side `productId`; lists can be large. Alternative: `?ci=` for the selected CI. Rejected at the user gate.

REST: additive implementation of an already-documented query param. SQL / Liquibase: none. OpenAPI: none.

### D2. Fit the filter into existing EventQueryService — no hexagonal refactor

**ADR-001** (`docs/adr/ADR-001-hexagonal-architecture.md`) applies because list-events **behavior** changes. **Do not** invent `ListEventsUseCase`, a new inbound port, or a `ProductCiLookupPort` for this additive predicate.

**Dependency direction (as-is, not newly introduced):**

- Domain (`processing.domain`) is not touched; it stays free of Spring/JPA/Jackson/Kafka/HTTP for this change.
- Application hexagonal services are not the list-events path. Console query stays in `processing.api`.
- `EventQueryService` already depends on Spring, JPA Criteria, Jackson, `EventJpaRepository`, `EventSourceRepository`, and `UserRepository`. This change adds `ProductCiRepository` (`cmdb.persistence`) the same way — a persistence collaborator on the console query service, not a new outbound port in `application/port/out`.
- Adapters: existing JPA repositories implement persistence. No new adapter class.
- Spring wiring: constructor injection on `EventQueryService`; no new `infrastructure/config` bean.

**ADR-001 six-part checklist:**

1. **Use cases and inbound ports** — No new use case. List-events remains `EventController` → `EventQueryService.listEvents(...)`. **Deviation:** the console REST surface was left out of the processing hexagonal migration; this change does not move it.
2. **Inbound adapters** — Existing `EventController` (`GET /api/v1/events`). Add optional `@RequestParam UUID productId`. **Deviation:** controller lives in `processing.api`, not `adapter/in`.
3. **Outbound ports** — None added. **Deviation:** `EventQueryService` continues to call repositories directly rather than a `ProductCiLookupPort` / `EventQueryPort`.
4. **Outbound adapter implementations** — Reuse `ProductCiRepository.findCiIdsByProductId` and `EventJpaRepository.findAll(Specification, Pageable)`. Empty CI list → a never-matching predicate (`cb.disjunction()`), not an omitted filter and not an exception.
5. **Infrastructure wiring** — Add `ProductCiRepository` to the existing `EventQueryService` constructor. No new `@Configuration`. Component scan already picks up both types.
6. **Spring-free use-case tests** — **N/A / deviation.** Do not extract a use case solely to test with port doubles. Coverage is MockMvc on `EventControllerTest` (own CIs included, other products excluded, empty/unknown product → empty page, AND with `severity`). Existing `listEventsWithoutAuthReturns401` stays green; 401 is not new behavior. `architecture-hexagonal-processing` still holds: console query stays out of that layout.

Unknown but well-formed UUID: `findCiIdsByProductId` returns empty → empty page, 200. Malformed `productId` (not a UUID): Spring conversion → 400; the SPA MUST NOT send those values.

### D3. Console: own helper next to FM-19, not a copy of that feature

Keep `applySeverityQueryParam` and `applyProductIdQueryParam`. Add `applyCiIdQueryParam` on `ConsolePageComponent`, called from the same `queryParamMap` subscription **before** the first `listEvents` / `interval(...).pipe(startWith(0), ...)`. Do **not** copy `applyProductIdQueryParam` as “this feature”.

`productId` (unchanged):

- Valid UUID (RFC 4122 `8-4-4-4-12` hex, case-insensitive — same shape as `sankey-layout.ts` `UUID_RE`, but a **private** check on the console page; do not import from Sankey): replace any existing `productId` chip with `{ field: 'productId', operator: 'eq', value }`.
- Empty, missing, or non-UUID: do not add a `productId` chip (and do not send `productId` on `listEvents`).
- Subscribe, not snapshot-only, so in-app navigation to a new `?productId=` still applies (same reason as FM-19).
- Clearing the chip uses existing query-bar × → `onFilterChange` → `loadEvents()` without `productId`. Do not rewrite the address bar (same trade-off as FM-19). Reload of the same URL re-applies the chip.
- `?severity=` and `?productId=` together: both chips; `buildEventApiParams` sends both.

`ciId`:

- Valid UUID: replace any existing `ciId` chip with `{ field: 'ciId', operator: 'eq', value }`.
- Empty, missing, or non-UUID: do not add a `ciId` chip (and do not send `ciId` on `listEvents`).
- Clearing the chip → subsequent `listEvents` without `ciId`.
- AND with existing `severity` and `productId` if present.
- Do **not** also read legacy `ci`.

Extend `QueryField` with `'productId'` and `'ciId'` and matching `QUERY_FIELD_LABELS` so the chips render. The add-form `<select>` is hardcoded to severity / status / title — keep it that way; do **not** add `productId` or `ciId` options (operators get these chips from the URL, not by typing a UUID).

`buildEventApiParams`: for `operator === 'eq'` and `field === 'productId'`, set `params['productId']`; for `field === 'ciId'`, set `params['ciId']`. `FmApiService.listEvents` already forwards the record.

`applyClientEventFilter` MUST ignore `productId` and `ciId` chips. Today it only client-filters `ne` / `title`; `eq` chips including `productId` / `ciId` are server-side. Do **not** match `productId` against `event.ciId`. Do **not** match `ciId` against event fields on the client — server filter only.

Alternative: new `ConsoleQueryParamService`. Rejected — no new Angular services.

### D4. Health links: product id for product-scoped; ciId for operative-center CI links

`CiHealthTabComponent` today: `[queryParams]="{ ci: ci.id }"`. It is used only from `OperativeCenterPanelComponent`, which already has `@Input() product`. Pass `[productId]="product.id"` into the tab and set `[queryParams]="{ productId: productId }"`. Applies on both `/health` (selected heatmap product) and `/health/:productId`.

`HealthProductPageComponent` header (`top-actions`): add «В консоль» for every role that can see the card. Place it **outside** the existing `@if (isAdmin())` block (that block is edit / weights / delete). `[routerLink]="['/console']"` + `[queryParams]="{ productId: product.id }"`.

Change «Открыть в консоли →» / «Консоль →» on the signals/events tabs from `{ ci: selectedCi.id }` to `{ ciId: selectedCi.id }` (API/OpenAPI name is `ciId`, not `ci`).

### D5. Tests: backend first, then frontend unit, then Playwright

TDD per module. Do not run adapter / simulator tests as required.

**backend/fm-module:** extend `EventControllerTest` (or a focused sibling). Create **two unique CIs** (`ProductAdminControllerTest.createConfigurationItem` with distinct FQDNs) and two products; bind via PATCH `ciIds`. Do **not** reuse only seed `demo-server.wisla.local` (it may already sit on other products). Persist `EventJpaEntity` with those `ciId`s, or ingest with `nodeFqdn` equal to each new CI’s FQDN (`EventControllerTest.ingestEvent` today always uses the seed FQDN). Assert:

- `GET /api/v1/events?productId=<A>` contains events of A’s CIs and excludes B’s.
- Product with no CIs, or unknown UUID → `items` empty, 200.
- `productId` + `severity` AND.
- Unauthenticated still 401.

**frontend Vitest:** `console-product-query.test.ts` analog of `console-severity-query.test.ts` (real query-bar, `ActivatedRoute`, `listEvents` args). Plus `console-ci-query.test.ts` for `ciId` (valid UUID chip + `listEvents`, clear chip, empty/non-UUID, AND with severity, add-form still only severity/status/title). Plus a health test: `CiHealthTabComponent` / product-page header `queryParams.productId`, not `ci`; `OperativeCenterPanelComponent` «Открыть в консоли →» / «Консоль →» `queryParams.ciId` equals selected CI id.

**Playwright:** spec `health-signals-product-filter.spec.ts`. Create two products with **distinct** CIs (unique FQDNs; reuse `health-graph.spec.ts` create/bind flow or admin API). Ingest titled events with those FQDNs (`event-duty-actions` ingest fixture pattern, not seed-only `demo-server`). Click «Перейти к сигналам» and/or «В консоль»; assert URL, chip, `GET /api/v1/events?productId=`, table **membership** (A’s event present, distractor absent — not “exactly one row”). From product page, open «Сигналы» tab, click «Открыть в консоли →» → `/console?ciId=<that CI uuid>`, chip visible, table contains that CI’s event and not a distractor on another CI. Direct URL; clear chip; invalid param; AND with severity. Backend must be up. Health graph visuals stay as they are (no assertion that Sankey/heatmap changed).

## Risks / Trade-offs

- [First poll unfiltered] → Apply `productId` (and existing `severity`) from `queryParamMap` before `startWith(0)` / `loadEvents()`.
- [Empty `IN ()` SQL] → If `findCiIdsByProductId` is empty, use `cb.disjunction()` (never-match) instead of `in(emptyList)`.
- [Cross-context repo on EventQueryService] → Accepted deviation; same pattern as `EventSourceRepository` / `UserRepository` already on that class. Do not start a hexagonal move of console query in this change.
- [URL still has `?productId=` after chip clear] → Accepted (FM-19 same); next `listEvents` uses chip state. Reload re-applies.
- [Malformed UUID to API] → SPA never sends; Spring 400 if someone does. Unknown UUID is empty page.
- [Operative-center CI links] → In scope: `{ ciId: selectedCi.id }`; console `applyCiIdQueryParam`. Legacy `ci` stays ignored.
- [`ciId` null events] → Excluded by the IN predicate; correct for “signals of the product’s CIs”.
- [Client filter `productId` vs `ciId`] → Do not treat the chip value as a CI id in `applyClientEventFilter`.
- [«В консоль» inside `isAdmin()`] → Link must remain visible to дежурный / специалист.

## Module changes

### frontend/

| Touch point | Change |
|---|---|
| `CiHealthTabComponent` | `@Input() productId`; «Перейти к сигналам» → `{ productId }` |
| `OperativeCenterPanelComponent` | Pass `product.id` into the health tab; «Открыть в консоли» / «Консоль →» → `{ ciId: selectedCi.id }` |
| `HealthProductPageComponent` | Header «В консоль» with `{ productId: product.id }`, outside `isAdmin()` |
| `ConsolePageComponent` | `applyProductIdQueryParam` and `applyCiIdQueryParam` next to severity; UUID check; before first `listEvents`; leave `mapId`/`ci` ignored |
| `query-bar.models.ts` | `QueryField` + labels for `productId` and `ciId`; do not add to add-form options |
| `query-filter.util.ts` | `buildEventApiParams` maps `productId=eq` → `productId` and `ciId=eq` → `ciId`; do not client-filter either against event fields |
| `FmApiService.listEvents` | No code change |
| Vitest | Console productId/ciId chips + health link `queryParams` |
| Playwright | Health → console productId; signals tab → `?ciId=`; direct URL; chip clear; AND severity |

### backend/fm-module

| Touch point | Change |
|---|---|
| `EventController.listEvents` | Optional `@RequestParam UUID productId`; pass through |
| `EventQueryService` | Inject `ProductCiRepository`; `buildSpec` ANDs `ciId IN ciIds` or never-match if empty |
| Liquibase / OpenAPI | None |
| Tests | Product CI membership / exclusion / empty page |

### backend/adapter, zabbix-simulator, prototype

None.

## REST / SQL

- REST: implement documented `GET /api/v1/events?productId=<uuid>`. No new endpoints. `severity` unchanged. No OpenAPI diff.
- SQL: none (existing `product_ci` + `events.ci_id`).

## Migration Plan

Deploy frontend + fm-module together so health links are not `?productId=` against an API that ignores it (old backend would open the full list). Rollback: revert both. No feature flag, no data migration.

## Open Questions

None blocking. Assumed: RFC 4122 UUID for the chip; chip clear does not sync the URL; `productId` is URL-seeded only (not in the add-filter form); events with null `ciId` are out of the product filter.

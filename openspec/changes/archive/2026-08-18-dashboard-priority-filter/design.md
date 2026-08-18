## Context

Dashboard `/` (`DashboardPageComponent`) already renders four severity cards from `GET /api/v1/dashboard/summary` (`severityCounts.critical|major|minor|warning`) and links each to `/console` with `[queryParams]="{ severity: item.key }"`. That URL is correct.

`ConsolePageComponent` (`frontend/src/app/pages/console/console-page.component.ts`) does **not** inject `ActivatedRoute` and never reads query params. `ngOnInit` loads maps and starts polling / `loadEvents()` with `eventParams()` from an empty `filter` signal (`chips: []`). Operators land on an unfiltered table.

The query-bar already supports the needed filter: a `QueryChip` `{ field: 'severity', operator: 'eq', value }` is shown as a removable chip; `buildEventApiParams` maps it to `GET /api/v1/events?severity=<value>`. `EventQueryService.buildSpec` already equality-filters `severity`. No API or schema change.

«Карта событий» is the left sidebar of `/console` (`selectMap` / `selectedMapId`), not a separate route. `mapId` / `ci` query params remain ignored (out of scope).

Existing specs `console-column-sort` and `console-last-repeat-column` stay as-is (sort headers / last-repeat column). There is no `dashboard` or `events` capability spec to delta.

## Goals / Non-Goals

**Goals:**

- Console reads `severity` from `ActivatedRoute.queryParamMap` and applies it as the existing query-bar `severity=eq` chip **before** the first `listEvents`.
- List loads via existing `FmApiService.listEvents` → `GET /api/v1/events?severity=…`. Chip visible and clearable (×).
- AC1–AC2: dashboard click Critical/Major/Minor/Warning → `/console?severity=<key>`, chip set, table only that severity.
- AC3: direct URL `/console?severity=major` applies the filter without a dashboard click.
- AC4: clearing the chip restores the unfiltered list (current map / polling).
- AC5: dashboard cards and `severityCounts` / `GET /dashboard/summary` unchanged.
- Frontend-only TDD: Vitest then implement then Playwright. No `mvn test`.

**Non-Goals:**

- Redesign dashboard; new widgets; move counters onto the event-map sidebar.
- Change `GET /dashboard/summary` or count calculation.
- Fix `mapId` / `ci` query params.
- New Angular services, routes, or query-bar operators.
- Backend, adapter, simulator, prototype, Liquibase, OpenAPI, Docker.

## Decisions

### D1. Frontend-only; no new services

`state.modules` = `frontend/` only. Touch `ConsolePageComponent`. Do **not** change `DashboardPageComponent`, `QueryBarComponent`, `query-filter.util.ts`, or `FmApiService` unless a tiny helper is strictly needed (prefer a private method on the console page).

Integration (contract unchanged):

```text
SPA ──REST JWT──► fm-module :8080
  GET /api/v1/dashboard/summary          (unchanged; dashboard cards)
  GET /api/v1/events?severity=<key>      (already supported)
```

Adapter → fm-module ingest is not in this change. REST contract: **none**. SQL / Liquibase: **none**. OpenAPI: **none**.

**ADR-001 hexagonal checklist: N/A** — frontend-only; no new and no materially changed backend behavior. Do not invent fake use cases.

1. Use cases / inbound ports — N/A
2. Inbound adapters — N/A
3. Outbound ports — N/A
4. Outbound adapter implementations — N/A
5. Infrastructure wiring — N/A
6. Spring-free use-case tests — N/A

Dependency direction (domain / application / adapters / `infrastructure/config`) is not documented: backend is not changed.

### D2. Seed `filter` from `queryParamMap` before first `listEvents`

Inject `ActivatedRoute` on `ConsolePageComponent`. On `queryParamMap`:

- If `severity` is one of `SEVERITY_OPTIONS` (`fatal`, `critical`, `major`, `minor`, `warning`, `normal`), set `filter` to include a chip `{ field: 'severity', operator: 'eq', value }` (replace any existing `severity` chip; leave status/title chips, text search, time range, map, sort as they are).
- If missing, blank, or unknown — do not add a severity chip.

Subscribe (not snapshot-only) so in-app navigation to a new `?severity=` still applies. The first `queryParamMap` emission is synchronous: apply the chip **before** starting `interval(...).pipe(startWith(0), switchMap(() => listEvents(eventParams())))` / `loadEvents()`, otherwise the first poll is unfiltered.

`eventParams()` already calls `buildEventApiParams(this.filter(), …)` — polling and manual refresh inherit the chip with no extra wiring.

Do not mutate the URL when the operator clears the chip (AC4 is list + chip, not address-bar sync). A full reload of the same `?severity=` URL re-applies the filter (deep-link).

Alternative considered: snapshot only in `ngOnInit`. Rejected — dashboard → console while the console component is reused would miss later param changes. Alternative: new `ConsoleQueryParamService`. Rejected — no new services.

### D3. Reuse query-bar chip UI; dashboard links stay

Bind remains `[filter]="filter()"`. `QueryBarComponent`’s `@Input() filter` copies chips into the chips row (`severity = <key>` + `.chip-remove`). Clearing uses existing `removeChip` → `filterChange` → `onFilterChange` → `loadEvents()` without `severity` in params.

Do not edit dashboard templates/links. Do not move counters. Event-map sidebar stays map presets only.

Alternative: client-only filter of an unfiltered `listEvents`. Rejected — `buildEventApiParams` already sends `severity` to the API; AC requires the list to load filtered.

### D4. Tests: Vitest then code then Playwright; skip Maven

No backend test tasks. Existing `console-page.component.test.ts` stubs `QueryBarComponent` and uses `provideRouter([])` — keep those duty-panel tests; `inject(ActivatedRoute)` with empty params must not break them.

New Vitest (same file or `console-severity-query.test.ts`): real `QueryBarComponent` (or assert `filter().chips` **and** `listEvents` args), `ActivatedRoute` / router with `severity`, mixed-severity mock page:

- `?severity=critical|major|minor|warning` → chip + `listEvents` called with `severity` (objectContaining).
- Direct param without dashboard.
- Missing / invalid `severity` → no chip, no `severity` query on `listEvents`.
- `.chip-remove` → later `listEvents` without `severity`.

Playwright: click dashboard card → URL + chip + filtered table; `goto('/console?severity=major')`; clear chip; return to `/` and still see the four count cards (AC5). Ingest mixed severities if the demo dump is not enough (same ingest helper pattern as `event-duty-actions.spec.ts`). Backend must be up for e2e.

## Risks / Trade-offs

- [First poll unfiltered] → Apply `queryParamMap` before `startWith(0)` / `loadEvents()`.
- [Query-bar stub hides chips in old tests] → New specs use real `QueryBarComponent` or assert `filter()` + `listEvents`; e2e asserts `.chip` / `.chip-remove`.
- [URL still has `?severity=` after clear] → Accepted; next `listEvents` uses chip state. Reload re-applies (deep-link).
- [Unknown `severity`] → Ignore; do not invent a chip.
- [`mapId` still ignored] → Explicit non-goal; do not “fix while here”.

## Module changes

### frontend/

| Touch point | Change |
|---|---|
| `ConsolePageComponent` | `inject(ActivatedRoute)`; apply `severity` query param to `filter` chips before first `listEvents`; subscribe for later param changes |
| `QueryBarComponent` | No code change; chip display / × already work via `[filter]` |
| `query-filter.util.ts` / `buildEventApiParams` | No code change |
| `DashboardPageComponent` | No code change (links already `[queryParams]="{ severity: item.key }"`) |
| `FmApiService.listEvents` | No code change |
| Vitest | Query param → chip + `listEvents` |
| Playwright | Counter click, direct URL, chip clear, dashboard cards still present |

### backend/fm-module, adapter, simulator, prototype

None.

## REST / SQL

- REST: no new endpoints, no new query params, no OpenAPI edits. Console uses existing `GET /api/v1/events?severity=`. Dashboard keeps existing `GET /api/v1/dashboard/summary`.
- SQL: none.

## Migration Plan

SPA-only. No migration, no rollback beyond reverting the console page change. No feature flag.

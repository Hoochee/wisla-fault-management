## Why

Dashboard `/` already sends operators to `/console?severity=<key>` (critical / major / minor / warning), but `ConsolePageComponent` never reads query params. The console opens unfiltered, so the 1-click filtered console required by `docs/pages-spec.md` does not work. FM-19: apply the existing query-bar `severity=eq` chip from the URL so the list loads filtered.

## What Changes

- Console `/console` reads `severity` from `ActivatedRoute.queryParamMap` and seeds the existing query-bar chip `severity=eq` with that value.
- Event list loads through existing `FmApiService.listEvents` → `GET /api/v1/events?severity=…` (`buildEventApiParams` already maps `severity=eq` chips).
- Chip is visible and clearable; clearing it reloads the unfiltered list (current map / polling unchanged).
- Dashboard severity-card links stay as-is (`[routerLink]="['/console']"` + `[queryParams]="{ severity: item.key }"`).
- Vitest: query param → chip + `listEvents` params. Playwright: click counter → URL + chip + filtered list.

### Non-goals

- Do not change `severityCounts` / `GET /dashboard/summary`.
- No new dashboard widgets; do not redesign dashboard; do not move counters onto the event-map sidebar.
- Do not treat «Карта событий» as a separate route — it is the left sidebar presets on `/console`.
- Do not fix ignored `mapId` / `ci` query params.
- No backend, adapter, simulator, prototype, Liquibase, OpenAPI, Docker, or new Angular services/routes.

## Capabilities

### New Capabilities

- `console-severity-query-filter`: Console applies `?severity=` from the URL as the existing query-bar `severity=eq` chip and loads `GET /api/v1/events?severity=…`. Covers dashboard 1-click and direct URL. Existing `console-column-sort` and `console-last-repeat-column` are unchanged (sort / last-repeat column only).

### Modified Capabilities

- None. There is no `dashboard` or `events` spec in `openspec/specs/`; dashboard links and `GET /api/v1/events?severity=` already match the product contract.

## Impact

- **`frontend/`**: `ConsolePageComponent` (`frontend/src/app/pages/console/console-page.component.ts`) — `ActivatedRoute.queryParamMap` → `filter` signal / `QueryBarComponent` chips → existing `eventParams()` / `listEvents`. Query-bar, `buildEventApiParams`, dashboard page, and `FmApiService` stay as-is.
- **Angular routes:** `/` and `/console` unchanged; only `/console` query-param handling is added.
- **Vitest:** extend console unit tests. **Playwright:** new or extended e2e for counter click and direct URL.
- **Не затрагиваются:** `backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, Liquibase, OpenAPI (`docs/fm-module/api.yaml`), Docker, `prototype/`, `demo/gift-shop/`.

## 1. frontend/ — Vitest (red)

- [x] 1.1 Add `frontend/tests/unit/console-severity-query.test.ts` (or extend `console-page.component.test.ts` with a setup that does **not** stub `QueryBarComponent`). Mock `FmApiService.listEvents` / `listEventMaps` and provide `ActivatedRoute` / router `queryParams` `{ severity }`. Covers **Dashboard Critical opens filtered console**, **Dashboard Major Minor Warning open filtered console**, and **Direct URL applies severity without dashboard click**: for `critical`, `major`, `minor`, `warning` the query-bar has chip `severity = <key>` and `listEvents` is called with `objectContaining({ severity: <key> })` before/on first load — red
- [x] 1.2 Same spec, **Clearing the severity chip restores the unfiltered list**: after `?severity=major`, click `.chip-remove`; chip gone; a subsequent `listEvents` is called without `severity` — red
- [x] 1.3 Same spec: missing `severity` and unknown value (e.g. `not-a-severity`) add no severity chip and do not pass `severity` to `listEvents`. Existing duty-panel tests in `console-page.component.test.ts` still compile against `inject(ActivatedRoute)` — red

## 2. frontend/ — ConsolePageComponent (green)

- [x] 2.1 In `ConsolePageComponent`, `inject(ActivatedRoute)` and apply `queryParamMap` `severity` (if in `SEVERITY_OPTIONS`) as chip `{ field: 'severity', operator: 'eq', value }` **before** the first `listEvents` / polling `startWith(0)`. Do not change `DashboardPageComponent`, `QueryBarComponent`, `buildEventApiParams`, or `FmApiService`
- [x] 2.2 Subscribe to `queryParamMap` so a later in-app `?severity=` change updates the chip and reloads; ignore blank/unknown; do not write `mapId`/`ci`; do not add a new Angular service
- [x] 2.3 Run `cd frontend && npm test` — green for 1.1–1.3 and existing console duty-panel tests

## 3. frontend/ — Playwright e2e (backend up)

- [x] 3.1 Add `frontend/tests/e2e/dashboard-priority-filter.spec.ts`: ingest (or use) mixed-severity events; login; click dashboard Critical card. Covers **Dashboard Critical opens filtered console**: URL `/console?severity=critical`, visible `severity = critical` chip, table rows only critical. Repeat for major/minor/warning (**Dashboard Major Minor Warning open filtered console**)
- [x] 3.2 Same spec, **Direct URL applies severity without dashboard click**: `goto('/console?severity=major')` applies chip and filtered list without clicking the dashboard
- [x] 3.3 Same spec, **Clearing the severity chip restores the unfiltered list**: × on the chip removes it; list is no longer severity-filtered (current map / polling)
- [x] 3.4 Same spec, **Dashboard severity counts stay unchanged**: after the filtered-console flow, `goto('/')` still shows the four Critical/Major/Minor/Warning cards from `GET /api/v1/dashboard/summary` (no new widgets)
- [x] 3.5 Run `cd frontend && npm run test:e2e` against running backend (`http://localhost:8080`) — green. Skip `mvn test` (frontend-only)

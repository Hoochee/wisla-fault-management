## Why

«Перейти к сигналам» on `/health` and `/health/:productId` currently navigates to `/console?ci=<ciId>`, but the console never reads `ci` (FM-19 only applies `severity`). Operators see the full event list instead of signals for that product’s CIs. OpenAPI already documents `GET /api/v1/events?productId=`, but `EventController` / `EventQueryService` ignore it. FM-21: filter the console by the product’s CIs (`ciId ∈ product CIs`) from product-scoped links. Operative-center «Сигналы» / «События» links additionally open the console filtered by that specific CI (`?ciId=`).

## What Changes

- Health UI: `CiHealthTabComponent` «Перейти к сигналам» uses `productId` of the current product (not `{ ci: ci.id }`). Product card `/health/:productId` header adds «В консоль» with the same `?productId=`. Operative-center «Сигналы» / «События» links («Открыть в консоли →» / «Консоль →») use `{ ciId: selectedCi.id }` so the console is filtered to that CI.
- Console `/console` reads `productId` from `ActivatedRoute.queryParamMap` (pattern from FM-19 `applySeverityQueryParam`, implemented as its own helper — not a copy of that feature). Valid UUID → removable query-bar chip **before** the first `listEvents` / poll. Empty or non-UUID → no chip. Clearing the chip → later `listEvents` without `productId`. Coexists with `?severity=` (AND).
- Console also reads `ciId` the same way via a separate `applyCiIdQueryParam` (do not copy `applyProductIdQueryParam` as this feature). Valid RFC-4122 UUID → removable chip `ciId = <uuid>` before first `listEvents` / poll; empty / non-UUID → no chip; clearing the chip → later `listEvents` without `ciId`. AND with existing `severity` and `productId` if present. Legacy `ci` stays ignored.
- Extend `QueryField` for the `productId` and `ciId` chips; `buildEventApiParams` passes `productId` / `ciId` to `FmApiService.listEvents` → `GET /api/v1/events?productId=` / `?ciId=`. Do not add either field to the query-bar add-form. Do not client-filter `productId` or `ciId` chips in `applyClientEventFilter` (server filter only).
- Backend: `EventController.listEvents` + `EventQueryService.buildSpec` filter events whose `ciId` is in `ProductCiRepository.findCiIdsByProductId`. Unknown UUID or empty CI set → empty page, not 500. No schema change. OpenAPI already has `productId` — no contract edit.

### Non-goals

- FM-19 dashboard priority counters / `?severity=` behavior (already done); do not merge with that change.
- Health formula, graph, snapshot, heatmap, weights, product CRUD.
- Ignored `mapId` and legacy `ci` query params (the SPA must not also read `ci`; CI-scoped filter uses OpenAPI `ciId` only).
- Adapter, simulator, Docker, Liquibase, prototype, new top-level services.

## Capabilities

### New Capabilities

- `console-product-query-filter`: Health pages deep-link to `/console?productId=<uuid>`; console applies that param as a query-bar chip and loads `GET /api/v1/events?productId=`; fm-module returns only events whose `ciId` belongs to that product’s CIs. Coexists with existing `console-severity-query-filter` (AND). Operative-center «Сигналы» / «События» links open `/console?ciId=<selected CI uuid>`; console applies `ciId` as its own chip and `GET /api/v1/events?ciId=` (AND with `severity` / `productId` when present). `mapId` / legacy `ci` stay ignored.

### Modified Capabilities

- None. `console-severity-query-filter` requirements are unchanged (severity chip, dashboard cards, ignored `mapId`/`ci`). `product-health-graph` / `health-product-crud` requirements (formula, snapshot, Sankey, weights, CRUD) are unchanged; this change only adds navigation to the console.

## Impact

- **`frontend/`**: `CiHealthTabComponent` (pass current `product.id` into the signals link); `HealthProductPageComponent` header «В консоль»; `OperativeCenterPanelComponent` forwards `product.id` into `CiHealthTabComponent` and changes «Открыть в консоли» / «Консоль →» to `{ ciId: selectedCi.id }`; `ConsolePageComponent` (`applyProductIdQueryParam` and `applyCiIdQueryParam` alongside existing severity); `QueryField` / `QUERY_FIELD_LABELS`; `buildEventApiParams`. Routes `/health`, `/health/:productId`, `/console` unchanged. `FmApiService.listEvents` already forwards params.
- **`backend/fm-module`**: `EventController.listEvents` accepts documented `productId`; `EventQueryService` applies `ciId IN product CIs` via existing `ProductCiRepository.findCiIdsByProductId`. REST: additive implementation of already-documented `GET /api/v1/events?productId=`. No Liquibase. No OpenAPI edit (`docs/fm-module/api.yaml` already lists `productId`).
- **Tests:** `cd backend/fm-module && mvn test` (productId: own CIs / other products excluded / empty product → empty page). Frontend Vitest analog of `console-severity-query.test.ts`. Playwright e2e mandatory. Do not require adapter / simulator tests.
- **Не затрагиваются:** `backend/adapter`, `backend/zabbix-simulator`, Docker, Liquibase, `prototype/`, `demo/gift-shop/`.

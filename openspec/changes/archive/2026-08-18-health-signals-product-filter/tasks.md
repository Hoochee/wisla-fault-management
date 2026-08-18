## 1. backend/fm-module — tests (red)

- [x] 1.1 Add tests in `EventControllerTest` (or a focused sibling under `processing/api`): two unique CIs (`createConfigurationItem` + distinct FQDNs), two products, PATCH `ciIds`. Persist `EventJpaEntity` with those `ciId`s or ingest with matching FQDNs — do not reuse only seed `demo-server.wisla.local`. Covers **Events of the product CIs are returned** and **Other products events are excluded**: `GET /api/v1/events?productId=<A>` includes A’s event and excludes B’s — red
- [x] 1.2 Same spec, **Empty or unknown product returns empty page**: product with no CIs and a random well-formed UUID each return HTTP 200 with empty `items` (not 500) — red
- [x] 1.3 Same spec, **API ANDs productId with severity**: product A has major and critical events on its CIs; `?productId=<A>&severity=major` includes major and excludes that product’s critical — red

## 2. backend/fm-module — EventController / EventQueryService (green)

- [x] 2.1 Add optional `@RequestParam UUID productId` on `EventController.listEvents` and pass it to `EventQueryService.listEvents`. Inject `ProductCiRepository`. In `buildSpec`, if `productId != null`, load `findCiIdsByProductId`; empty list → never-match (`cb.disjunction()`); otherwise `ciId IN ciIds`. AND with existing `severity`. Do not change Liquibase or OpenAPI
- [x] 2.2 Run `cd backend/fm-module && mvn test` — green for 1.1–1.3 and existing event tests. Do not require adapter / simulator tests

## 3. frontend/ — Vitest (red)

- [x] 3.1 Add `frontend/tests/unit/console-product-query.test.ts` analog of `console-severity-query.test.ts` (do not stub `QueryBarComponent`). Covers **Direct URL applies productId without a health click** and **Health link opens filtered console with productId chip**: valid UUID → chip `productId = <uuid>` and `listEvents` called with `objectContaining({ productId })` on first load. Also covers **Add-filter form does not offer productId**: add-form field select has severity / status / title only — red
- [x] 3.2 Same spec, **Clearing the productId chip removes the filter**: after a valid `?productId=`, click `.chip-remove`; chip gone; a subsequent `listEvents` is called without `productId` — red
- [x] 3.3 Same spec, **Empty or non-UUID productId adds no chip**: missing, empty, and `not-a-uuid` add no productId chip and do not pass `productId` to `listEvents` — red
- [x] 3.4 Same spec, **Console URL with productId and severity**: `?productId=<uuid>&severity=major` shows both chips and `listEvents` is called with both params. Existing `console-severity-query.test.ts` still passes — red
- [x] 3.5 Add/extend a health unit test: `CiHealthTabComponent` «Перейти к сигналам» has `queryParams.productId` (current product), not `ci`. `HealthProductPageComponent` header «В консоль» uses `{ productId: product.id }`. Covers **Перейти к сигналам uses productId not ci** and **Product card header В консоль uses productId** — red

## 4. frontend/ — console and health UI (green)

- [x] 4.1 Extend `QueryField` / `QUERY_FIELD_LABELS` with `productId`. In `buildEventApiParams`, map `productId=eq` → `productId`. Do not add `productId` to the query-bar add-form field list. Do not match `productId` against `event.ciId` in `applyClientEventFilter`
- [x] 4.2 In `ConsolePageComponent`, add `applyProductIdQueryParam` (do not copy `applySeverityQueryParam` as this feature). Valid UUID chip before first `listEvents` / poll; subscribe to `queryParamMap`; leave `mapId`/`ci` ignored
- [x] 4.3 Pass current `product.id` into `CiHealthTabComponent`; change «Перейти к сигналам» to `{ productId }`. Add `/health/:productId` header «В консоль» with the same param **outside** `@if (isAdmin())`. Do not change «Открыть в консоли» / «Консоль →» `{ ci: selectedCi.id }`
- [x] 4.4 Run `cd frontend && npm test` — green for 3.1–3.5 and existing console / health unit tests

## 5. frontend/ — Playwright e2e (backend up)

- [x] 5.1 Add `frontend/tests/e2e/health-signals-product-filter.spec.ts`: two products with distinct CIs (unique FQDNs; not seed-only `demo-server`). Ingest titled events with those FQDNs (ingest-source fixture). Covers **Перейти к сигналам uses productId not ci** / **Product card header В консоль uses productId**: click the health link → `/console?productId=<A uuid>`, chip visible, table contains A’s event and not the distractor (**Events of the product CIs are returned**, **Other products events are excluded**) — membership, not exclusive row count
- [x] 5.2 Same spec, **Direct URL applies productId without a health click**: `goto('/console?productId=<A>')` applies chip and filtered list without a health click
- [x] 5.3 Same spec, **Clearing the productId chip removes the filter** and **Empty or non-UUID productId adds no chip** (e.g. `goto('/console?productId=not-a-uuid')` has no productId chip)
- [x] 5.4 Same spec, **Console URL with productId and severity**: `?productId=<A>&severity=major` shows both chips and AND-filtered rows. **Health graph and snapshot stay unchanged**: after the flow, `/health/:productId` still shows the existing healthPercent / graph (no formula change)
- [x] 5.5 Run `cd frontend && npm run test:e2e` against running backend (`http://localhost:8080`) — green for this spec. Do not require adapter / simulator tests

## 6. frontend/ — ciId console filter (scope addendum)

- [x] 6.1 Add `frontend/tests/unit/console-ci-query.test.ts` analog of `console-product-query.test.ts`. Valid `?ciId=` UUID → chip `ciId = <uuid>` and `listEvents` with `{ ciId }` on first load. Add-form still only severity / status / title (not `ciId`, not `productId`). Clear chip → later `listEvents` without `ciId`. Empty / non-UUID → no chip. `?ciId=` + `?severity=` AND. Legacy `?ci=` ignored
- [x] 6.2 Health unit: `OperativeCenterPanelComponent` «Открыть в консоли →» and «Консоль →» `queryParams.ciId` equals selected CI id, not `ci`, not `productId`. Product-scoped links in 3.5 stay `{ productId }`
- [x] 6.3 Extend `QueryField` / `QUERY_FIELD_LABELS` with `ciId`. In `buildEventApiParams`, map `ciId=eq` → `ciId`. Do not add `ciId` to the query-bar add-form. Do not match `ciId` against event fields in `applyClientEventFilter`
- [x] 6.4 In `ConsolePageComponent`, add `applyCiIdQueryParam` (do not copy `applyProductIdQueryParam` as this feature). Valid UUID chip before first `listEvents` / poll; subscribe to `queryParamMap`; leave legacy `ci` ignored. Keep `productId` behavior unchanged
- [x] 6.5 Change `OperativeCenterPanelComponent` «Открыть в консоли →» and «Консоль →» to `{ ciId: selectedCi.id }`. Do not change `CiHealthTabComponent` «Перейти к сигналам» or header «В консоль» (`{ productId }`)
- [x] 6.6 Extend `frontend/tests/e2e/health-signals-product-filter.spec.ts`: from product page, open «Сигналы» tab, click console link → `/console?ciId=<that CI uuid>`, chip visible, table contains that CI’s event and not a distractor on another CI. Unique FQDNs; ingest-source fixture
- [x] 6.7 Run `cd frontend && npm test` — green for 6.1–6.5 and existing unit tests. Playwright 6.6 is written; full e2e is not required in this frontend-engineer pass if Docker is heavy

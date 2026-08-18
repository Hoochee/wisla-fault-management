# console-product-query-filter

## Purpose

Open the console from product health already filtered: by the product’s CIs (`?productId=`) or by a selected CI (`?ciId=`), with the filter in the URL and a clearable query-bar chip.

## Requirements

### Requirement: Health pages open console filtered by product CIs

On `/health` and `/health/:productId`, «Перейти к сигналам» in `CiHealthTabComponent` SHALL navigate to `/console?productId=<uuid>` of the **current product**, not `/console?ci=<ciId>`. The product card `/health/:productId` header SHALL include a «В консоль» link with the same `productId` query param, visible to every role that can view the card. Operative-center links «Открыть в консоли →» (Сигналы tab) and «Консоль →» (События tab) SHALL navigate to `/console?ciId=<selected CI uuid>` (OpenAPI name `ciId`, not `ci`). Health formula, graph, snapshot, heatmap, and weights MUST NOT change.

#### Scenario: Перейти к сигналам uses productId not ci

- **GIVEN** an operator on `/health` or `/health/:productId` with a current product uuid `P` and a selected CI
- **WHEN** they click «Перейти к сигналам»
- **THEN** the browser location is `/console?productId=P`
- **AND** the location does not use `ci=` for this navigation

#### Scenario: Product card header В консоль uses productId

- **GIVEN** an operator on `/health/:productId` for product uuid `P`
- **WHEN** they click «В консоль» in the card header
- **THEN** the browser location is `/console?productId=P`

#### Scenario: Сигналы tab opens console filtered by that CI

- **GIVEN** an operator on `/health/:productId` with a selected CI uuid `C`
- **WHEN** they open the «Сигналы» tab and click «Открыть в консоли →»
- **THEN** the browser location is `/console?ciId=C`
- **AND** the location does not use `ci=` or `productId=` for this navigation

#### Scenario: События tab Консоль link uses ciId

- **GIVEN** an operator on `/health/:productId` with a selected CI uuid `C`
- **WHEN** they open the «События» tab and click «Консоль →»
- **THEN** the browser location is `/console?ciId=C`

#### Scenario: Health graph and snapshot stay unchanged

- **GIVEN** a product card that already shows server `healthPercent`, components, and Sankey
- **WHEN** the operator uses «В консоль» or «Перейти к сигналам»
- **THEN** those health visuals and calculation are not altered by this change

### Requirement: Console applies productId query param as query-bar chip

When the operator opens `/console` with a `productId` query parameter, `ConsolePageComponent` SHALL read it from `ActivatedRoute.queryParamMap` and SHALL set a query-bar chip `productId=eq` to that value **before** the first `GET /api/v1/events` from that view, using a dedicated helper (pattern from `applySeverityQueryParam`, not a copy of the severity feature). The chip MUST be visible and clearable. The list MUST load through existing `FmApiService.listEvents` with `productId` in the request params (`buildEventApiParams` MUST map the chip). A value that is empty or not a UUID MUST NOT add a chip and MUST NOT be sent as `productId`. `mapId` and legacy `ci` query params MUST remain ignored. `QueryField` MUST include `productId` so the chip can render; the query-bar add-filter form MUST NOT offer `productId` as a selectable field. Client-side list filtering MUST NOT treat the `productId` chip value as a CI id.

#### Scenario: Health link opens filtered console with productId chip

- **GIVEN** `/console?productId=<valid-uuid>` after a health «Перейти к сигналам» or «В консоль» click
- **WHEN** the console view loads
- **THEN** the query-bar shows a removable chip `productId = <valid-uuid>`
- **AND** `listEvents` is called with `productId=<valid-uuid>` before or on first load
- **AND** the events table shows only events whose `ciId` belongs to that product’s CIs

#### Scenario: Direct URL applies productId without a health click

- **GIVEN** the operator is not coming from a health click
- **WHEN** they open `/console?productId=<valid-uuid>` directly
- **THEN** the query-bar shows a chip `productId = <valid-uuid>`
- **AND** `listEvents` is called with that `productId`
- **AND** the table shows only events of that product’s CIs

#### Scenario: Clearing the productId chip removes the filter

- **GIVEN** `/console` with chip `productId = <uuid>` and a list filtered to that product
- **WHEN** the operator clicks the chip remove control
- **THEN** the productId chip is gone
- **AND** a later `listEvents` in this view is called without a `productId` param

#### Scenario: Empty or non-UUID productId adds no chip

- **GIVEN** the operator opens `/console` with missing `productId`, `productId=` empty, or a non-UUID value
- **WHEN** the console applies query params
- **THEN** no `productId` chip is shown
- **AND** `listEvents` is called without a `productId` param

#### Scenario: Add-filter form does not offer productId

- **GIVEN** the console query-bar
- **WHEN** the operator opens the add-filter field select
- **THEN** the options are severity, status, and title
- **AND** `productId` is not among them

### Requirement: Console applies ciId query param as query-bar chip

When the operator opens `/console` with a `ciId` query parameter, `ConsolePageComponent` SHALL read it from `ActivatedRoute.queryParamMap` and SHALL set a query-bar chip `ciId=eq` to that value **before** the first `GET /api/v1/events` from that view, using a dedicated `applyCiIdQueryParam` helper (pattern from `applyProductIdQueryParam`, not a copy of that feature). The chip MUST be visible and clearable. The list MUST load through existing `FmApiService.listEvents` with `ciId` in the request params (`buildEventApiParams` MUST map the chip). A value that is empty or not a UUID MUST NOT add a chip and MUST NOT be sent as `ciId`. Legacy `ci` MUST remain ignored (the console MUST NOT also read `ci`). `QueryField` MUST include `ciId` so the chip can render; the query-bar add-filter form MUST NOT offer `ciId` as a selectable field. Client-side list filtering MUST NOT match the `ciId` chip against event fields (server filter only). When `severity` and/or `productId` are also present, the console SHALL apply all chips and `listEvents` SHALL send all corresponding params (AND).

#### Scenario: Сигналы tab link opens filtered console with ciId chip

- **GIVEN** `/console?ciId=<valid-uuid>` after a health «Открыть в консоли →» click from the «Сигналы» tab
- **WHEN** the console view loads
- **THEN** the query-bar shows a removable chip `ciId = <valid-uuid>`
- **AND** `listEvents` is called with `ciId=<valid-uuid>` before or on first load
- **AND** the events table shows that CI’s event and does not show a distractor event on another CI

#### Scenario: Clearing the ciId chip removes the filter

- **GIVEN** `/console` with chip `ciId = <uuid>` and a list filtered to that CI
- **WHEN** the operator clicks the chip remove control
- **THEN** the ciId chip is gone
- **AND** a later `listEvents` in this view is called without a `ciId` param

#### Scenario: Empty or non-UUID ciId adds no chip

- **GIVEN** the operator opens `/console` with missing `ciId`, `ciId=` empty, or a non-UUID value
- **WHEN** the console applies query params
- **THEN** no `ciId` chip is shown
- **AND** `listEvents` is called without a `ciId` param

#### Scenario: Add-filter form does not offer ciId

- **GIVEN** the console query-bar
- **WHEN** the operator opens the add-filter field select
- **THEN** the options are severity, status, and title
- **AND** `ciId` is not among them
- **AND** `productId` is not among them

#### Scenario: Console URL with ciId and severity

- **GIVEN** `/console?ciId=<valid-uuid>&severity=major`
- **WHEN** the console view loads
- **THEN** the query-bar shows both a `ciId` chip and a `severity = major` chip
- **AND** `listEvents` is called with both `ciId` and `severity=major`

### Requirement: GET /api/v1/events filters by product CI set

`GET /api/v1/events?productId=<uuid>` SHALL return a page of events whose `ciId` is in the set returned by `ProductCiRepository.findCiIdsByProductId` for that product (`ciId ∈ product CIs`). Events of CIs that belong only to other products MUST be absent. A well-formed UUID that is unknown or whose product has no CIs MUST yield an empty page with HTTP 200, not HTTP 500. Unauthenticated calls SHALL still return 401. The existing documented query param name is `productId`; the OpenAPI document MUST NOT need a schema change for this behavior. No Liquibase change.

#### Scenario: Events of the product CIs are returned

- **GIVEN** product A linked to CI-1 with an event on CI-1, and product B linked to CI-2 with an event on CI-2
- **WHEN** an authenticated client calls `GET /api/v1/events?productId=<A>`
- **THEN** the page includes the event on CI-1
- **AND** it does not include the event on CI-2

#### Scenario: Other products events are excluded

- **GIVEN** an event whose `ciId` is not in product A’s CI set
- **WHEN** the client lists events with `productId=<A>`
- **THEN** that event is not in the page

#### Scenario: Empty or unknown product returns empty page

- **GIVEN** a product with no linked CIs, or a well-formed UUID that is not a product
- **WHEN** the client calls `GET /api/v1/events?productId=<that-uuid>`
- **THEN** the response is HTTP 200
- **AND** `items` is empty

### Requirement: productId and severity filters combine with AND

When both `productId` and `severity` are present (URL chips and API query params), the console SHALL apply both chips and the API SHALL return only events that match **both** predicates. Existing `console-severity-query-filter` behavior for dashboard `?severity=` MUST remain. FM-19 dashboard priority counters MUST NOT change.

#### Scenario: Console URL with productId and severity

- **GIVEN** `/console?productId=<valid-uuid>&severity=major`
- **WHEN** the console view loads
- **THEN** the query-bar shows both a `productId` chip and a `severity = major` chip
- **AND** `listEvents` is called with both `productId` and `severity=major`

#### Scenario: API ANDs productId with severity

- **GIVEN** product A has a major event on one of its CIs and a critical event on one of its CIs
- **WHEN** the client calls `GET /api/v1/events?productId=<A>&severity=major`
- **THEN** the page includes the major event of A
- **AND** it does not include A’s critical event

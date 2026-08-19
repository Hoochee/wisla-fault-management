# product-health-graph Specification

## Purpose

Server-side product health (Monq formula without combo) inside fm-module: percents, component weights, Sankey, history heatmap, and Angular pages that consume the API snapshot instead of client fakes.

## Requirements

### Requirement: Health bounded context follows ADR-001 hexagonal layout

`ru.wisla.fm.health` SHALL be organized as `domain`, `application/port/in`, `application/port/out`, `application/service`, `adapter/in`, `adapter/out`, and `infrastructure/config`. Classes in `domain` and `application` MUST NOT import Spring, JPA, Jackson, Kafka, or HTTP types. `HexagonalArchitectureTest` MUST include `ru.wisla.fm.health..` in its in-scope packages.

#### Scenario: Domain calculator has no Spring

- **GIVEN** `HealthCalculator` in `ru.wisla.fm.health.domain`
- **WHEN** its source and bytecode dependencies are inspected
- **THEN** it has no Spring, JPA, Jackson, Kafka, or servlet imports
- **AND** `HealthCalculatorTest` instantiates it without a Spring context

#### Scenario: ArchUnit covers the health package

- **GIVEN** `backend/fm-module` architecture tests
- **WHEN** `mvn test` runs `HexagonalArchitectureTest`
- **THEN** layering rules apply to `ru.wisla.fm.health..`
- **AND** a domain class that imports `org.springframework` fails the build

### Requirement: Product health uses the Monq formula without combo

The system SHALL compute product and component health as `H = min(h_direct, h_ratio)` where `h_direct` is the minimum CI health among critical links whose health is below `critical_threshold` (default 100), and `h_ratio` is the weighted average `Σ k_i * h_i` with `k_i = weight_i / Σ weights`. Combo influence MUST NOT be applied.

#### Scenario: Weighted average when no critical breach

- **GIVEN** two weighted CIs with health 100 and 50 and equal weights
- **WHEN** `HealthCalculator` computes component health
- **THEN** `h_ratio` is 75
- **AND** `H` is 75

#### Scenario: Critical link caps health

- **GIVEN** a critical CI with health 25 and threshold 100, and weighted CIs that would yield `h_ratio` 80
- **WHEN** the calculator runs
- **THEN** `h_direct` is 25
- **AND** `H` is 25

#### Scenario: Zero total weight

- **GIVEN** all component weights are 0 and no critical links exist
- **WHEN** the calculator runs
- **THEN** product health is 100

### Requirement: Signal severity maps to CI health percent

The system SHALL map the worst open event severity on a CI to health percent: fatal 0, critical 25, major 50, minor 62, warning 75, and 100 when there are no open events. Closed and archived events MUST NOT affect the mapping.

#### Scenario: Worst open signal wins

- **GIVEN** a CI with open events of severities warning and critical
- **WHEN** CI health is derived
- **THEN** the CI health percent is 25

#### Scenario: Closed events ignored

- **GIVEN** a CI whose only events are closed or archived
- **WHEN** CI health is derived
- **THEN** the CI health percent is 100

### Requirement: Damage is computed for Sankey width

The system SHALL compute damage as `(100 - h) * k` for weighted links and `(100 - h)` for critical links. Equal minimum health values SHALL split damage evenly.

#### Scenario: Weighted damage

- **GIVEN** a weighted CI with health 50 and `k_i` 0.4
- **WHEN** damage is computed
- **THEN** damage is 20

#### Scenario: Critical damage ignores weight share

- **GIVEN** a critical CI with health 25
- **WHEN** damage is computed
- **THEN** damage is 75

### Requirement: Health snapshot and history are persisted

The system SHALL persist the current calculation in `product_health_snapshot` and append or upsert a history bucket in `product_health_history`. After recalculation the system SHALL copy `max_severity` and `active_event_count` from the snapshot onto `products`.

#### Scenario: Snapshot upsert

- **GIVEN** a product that was recalculated
- **WHEN** the use case completes
- **THEN** `product_health_snapshot` contains `health_percent`, `damage_percent`, `max_severity`, `active_event_count`, JSON `payload`, and `calculated_at` for that product

#### Scenario: History bucket

- **GIVEN** a recalculation falling into a 15-minute bucket
- **WHEN** history is written
- **THEN** `product_health_history` upserts `(product_id, bucket_start)` with min/max health and worst severity for that bucket

### Requirement: Health REST returns server-side percent, components, and Sankey

`GET /api/v1/health/products` and `GET /api/v1/health/products/{id}` SHALL return computed `healthPercent`, `damagePercent`, and component breakdown from the snapshot. Detail SHALL include `sankey.nodes` and `sankey.links` with `from`, `to`, and `damage`. Existing fields `maxSeverity`, `activeEventCount`, and `ciIds` MUST remain. Unauthenticated calls SHALL return 401.

#### Scenario: Heatmap list includes percents

- **GIVEN** an authenticated operator and a stored snapshot with health 75
- **WHEN** the client calls `GET /api/v1/health/products`
- **THEN** the product row includes `healthPercent` 75 and `damagePercent`
- **AND** `maxSeverity` and `activeEventCount` are still present

#### Scenario: Detail includes Sankey from the snapshot

- **GIVEN** a snapshot payload with component damages
- **WHEN** the client calls `GET /api/v1/health/products/{id}`
- **THEN** the body includes `sankey.links` whose widths equal server-computed damage
- **AND** the API does not require the client to invent components

#### Scenario: History endpoint

- **GIVEN** stored history buckets
- **WHEN** the client calls `GET /api/v1/health/products/{id}/history?from=&to=&bucketMinutes=15`
- **THEN** the response lists buckets with `minHealth`, `maxHealth`, and `worstSeverity`

#### Scenario: Unauthorized health read

- **GIVEN** no JWT
- **WHEN** a client calls `GET /api/v1/health/products`
- **THEN** the API returns 401

### Requirement: Recalculation runs on event lifecycle and on a schedule

The system SHALL recalculate health for products linked via `product_ci` when processing publishes in-process `EventCreated`, `EventUpdated`, or `EventClosed`, and SHALL run a full recalculation at least every 5 minutes.

#### Scenario: Event update triggers product recalc

- **GIVEN** a CI linked to a product
- **WHEN** an open event on that CI is created or its severity changes
- **THEN** `RecalculateProductHealthUseCase` runs for that product
- **AND** the snapshot `healthPercent` changes accordingly

#### Scenario: Scheduled full recalc

- **GIVEN** a snapshot older than the scheduler interval
- **WHEN** `HealthRecalcScheduler` fires
- **THEN** every product with components is recalculated

### Requirement: Angular health pages consume the API snapshot

Routes `/health` and `/health/:productId` SHALL render `healthPercent`, components, Sankey, and history from fm-module REST. `health-profile.util.ts` MUST NOT generate fake components or a synthetic timeline.

#### Scenario: Product card uses server components

- **GIVEN** `GET /api/v1/health/products/{id}` returns components POWER and CPU
- **WHEN** the operator opens `/health/:productId`
- **THEN** the UI shows those component names and percents
- **AND** it does not display invented CPU/HDD rows from `defaultComponents`

#### Scenario: Heatmap color follows healthPercent

- **GIVEN** the list API returns `healthPercent` 25 for a product
- **WHEN** the operator opens `/health`
- **THEN** the heatmap cell uses the server percent, not a client-synthesized value

### Requirement: Dashboard consumes the product health list snapshot

Dashboard `/` (`DashboardPageComponent`) SHALL render product health tiles from the same snapshot list as `/health`: existing `FmApiService.getProducts()` → `GET /api/v1/health/products` (mapped by `mapProductHealth`) for product id, name, and server `healthPercent`. For each product the SPA SHALL call existing `FmApiService.getProduct(id)` → `GET /api/v1/health/products/{id}` and list every `configurationItems` row with health percent from `buildProfiles(configurationItems, activeEvents)` (the same client derivation as `/health/:id`). Each tile MUST show the **same summary aside as the product-page health card** (product name kicker, large `healthPercent`, `HEALTH_LABELS[percentToLevel]`, «Анализ урона» from `damagePercent`, min/max from `minHealthToday` / `maxHealthToday`; no Sankey, no RCA button) and, below it, a **composition table** with columns **Название КЕ** (fqdn, not UUID), **Тип** (`ciType` as stored), **Здоровье** (`buildProfiles` percent) — not slot codes from list `components[]` (POWER / CPU / HDD / AVAILABILITY). Tile color MUST follow `percentToLevel(healthPercent)` (same mapping as the `/health` heatmap), not `maxSeverity`. Clicking a tile MUST navigate to `/health/:id`. The block header MUST keep the link to heatmap `/health`. The panel MUST list every product from the health response (no client `limit 5`). Product-health tiles MUST render **below** «Карты событий» (full-width stack: severity counters → event maps → tiles → «Всего продуктов» from `products.length`, not «Всего активных» / `summary.totalActive`), not side-by-side with maps. The SPA MUST NOT invent missing CIs, MUST NOT show raw UUIDs as the primary CI label, MUST NOT synthesize product `healthPercent` from events or severity, and MUST NOT call `GET /api/v1/health/ci/{id}`. FM-19 priority counters and event-map links MUST continue to use `GET /api/v1/dashboard/summary` unchanged; a health-list or product-detail failure MUST NOT blank those counters. Health formula, REST contract, snapshot persistence, heatmap, and product graph MUST NOT change.

#### Scenario: Tiles show server healthPercent from the health list

- **GIVEN** an authenticated operator and `GET /api/v1/health/products` returns a product with `healthPercent` 75
- **WHEN** the operator opens Dashboard `/`
- **THEN** `DashboardPageComponent` calls `FmApiService.getProducts()`
- **AND** the product tile displays the product-page summary: 75%, HEALTH_LABELS for `percentToLevel(75)` (Предупреждение), damage analysis, and min/max when the detail provides them
- **AND** the percent is not derived from `maxSeverity` or `activeEventCount`

#### Scenario: Tiles sit below event maps

- **GIVEN** Dashboard `/` with severity counters, «Карты событий», and «Здоровье продуктов»
- **WHEN** the operator views the page
- **THEN** the maps section appears before the product-health section in the document
- **AND** maps and tiles are stacked full width, not in a two-column `1fr 2fr` grid

#### Scenario: Tile shows all product CIs with buildProfiles percents

- **GIVEN** `GET /api/v1/health/products/{id}` for Gift Shop returns configuration items `giftshop-storefront.demo` (service), `giftshop-catalog.demo` (service), `giftshop-checkout.demo` (service), and `giftshop-postgres.demo` (database), plus active events
- **WHEN** the operator opens Dashboard `/`
- **THEN** `DashboardPageComponent` calls `FmApiService.getProduct(id)` for that product
- **AND** the tile lists those four CIs in a table (Название КЕ / Тип / Здоровье) by fqdn (not UUID) with percents from `buildProfiles`
- **AND** the database CI is included
- **AND** the tile does not use POWER / CPU / HDD / AVAILABILITY as CI row labels
- **AND** the SPA does not invent extra CIs

#### Scenario: Percents match the product page without client invention

- **GIVEN** the same `getProduct(id)` payload used by `/health/:id`
- **WHEN** the operator compares CI percents on Dashboard `/` and on `/health/:id`
- **THEN** each CI percent matches `buildProfiles(configurationItems, activeEvents)`
- **AND** Dashboard does not treat list `components[]` slots as CIs

#### Scenario: Click opens the product graph

- **GIVEN** a tile for product id `P` on Dashboard `/`
- **WHEN** the operator clicks the tile
- **THEN** the browser location is `/health/P`

#### Scenario: Heatmap link remains in the block header

- **GIVEN** Dashboard `/` with the «Здоровье продуктов» panel
- **WHEN** the operator clicks the header link to heatmap
- **THEN** the browser location is `/health`

#### Scenario: Priority counters and event maps stay on summary

- **GIVEN** `GET /api/v1/dashboard/summary` returns `severityCounts` and `systemMaps`
- **WHEN** the operator opens Dashboard `/`
- **THEN** Critical / Major / Minor / Warning counters still render from `severityCounts` and still link to `/console?severity=`
- **AND** «Карты событий» still lists `systemMaps`
- **AND** the health tiles are not bound to `productPreview`
- **AND** a failed `getProducts()` or `getProduct(id)` does not blank those counters

#### Scenario: Empty health list shows an empty panel

- **GIVEN** `GET /api/v1/health/products` returns `[]`
- **WHEN** the operator opens Dashboard `/`
- **THEN** the «Здоровье продуктов» panel is present with the `/health` header link
- **AND** no product tiles are rendered

#### Scenario: Missing snapshot is rendered with product-page percent semantics

- **GIVEN** a product whose detail has empty `configurationItems` and no active events (or list `healthPercent` 100)
- **WHEN** the operator opens Dashboard `/`
- **THEN** the tile shows the list product percent
- **AND** no invented CI rows (no Gift Shop FQDNs, no POWER / CPU / HDD / AVAILABILITY slots)

#### Scenario: All products from the health list are shown

- **GIVEN** `GET /api/v1/health/products` returns more than five products
- **WHEN** the operator opens Dashboard `/`
- **THEN** every product in that list has a tile
- **AND** the SPA does not slice to five rows from `productPreview`

#### Scenario: Tile color follows percentToLevel not maxSeverity

- **GIVEN** a list row with `healthPercent` 25 and `maxSeverity` warning
- **WHEN** the operator opens Dashboard `/`
- **THEN** the tile uses `percentToLevel(25)` (critical) for its health level styling
- **AND** it does not use `data-severity` of warning for that color

#### Scenario: Dashboard tiles use existing health REST only

- **GIVEN** this change is frontend-only
- **WHEN** the operator loads Dashboard `/`
- **THEN** tiles are sourced from existing `GET /api/v1/health/products` and `GET /api/v1/health/products/{id}`
- **AND** `GET /api/v1/dashboard/summary` is not extended
- **AND** `GET /api/v1/health/ci/{id}` is not called

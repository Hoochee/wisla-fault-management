## ADDED Requirements

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

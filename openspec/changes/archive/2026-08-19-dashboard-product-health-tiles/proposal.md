## Why

Dashboard `/` is the post-login entry, but it does not show product health of real configuration items. The previous tile preview (and `GET /api/v1/health/products` `components[]`) shows **slots** (POWER / CPU / HDD / AVAILABILITY), not Gift Shop CIs (storefront, catalog, checkout, postgres). Operators and demo presenters need every product CI — services **and** databases — with the same `%` as `/health/:id`, stacked **below** «Карты событий», not beside it.

## What Changes

- Replace the Dashboard product preview with tiles sourced from existing `FmApiService.getProducts()` → `GET /api/v1/health/products` (same list as `/health`) for product id, name, and product `healthPercent`.
- Each tile copies the **product-page health-summary aside** (large `%`, `HEALTH_LABELS`, «Анализ урона», min/max; product name as kicker). Do **not** embed Sankey / `app-monq-health-graph`; skip the disabled RCA button. Keep `damagePercent`, `minHealthToday`, `maxHealthToday`, and detail `healthPercent` on the tile view.
- For each product id, load `FmApiService.getProduct(id)` → `GET /api/v1/health/products/{id}` and show **all** `configurationItems` in a table: **Название КЕ** (fqdn), **Тип** (`ciType`), **Здоровье** (`buildProfiles` `%`) — the same client derivation as `/health/:id`. Gift Shop: storefront, catalog, checkout services **and** postgres database. Do not invent missing CIs; do not show slot codes (POWER/CPU/HDD/AVAILABILITY) as the main tile body.
- Layout: stack sections full width — severity counters → «Карты событий» → product health tiles → «Всего продуктов» (`products.length`). Tiles MUST sit **below** event maps, not side-by-side (`grid-template-columns: 1fr 2fr` for maps|tiles is forbidden). Tile grid minmax must be large enough for CI lists.
- Tile color follows server product `healthPercent` via existing `percentToLevel` (same mapping as the `/health` heatmap), not `maxSeverity`.
- Click on a tile navigates to `/health/:id`. Keep the existing header link to heatmap `/health`.
- Render **all** products from the health list (no `limit 5`). Empty list → empty panel. Missing snapshot / no events → same `%` semantics as the product page (`buildProfiles`).
- Leave FM-19 priority counters and event-map links unchanged. Loads are independent: health-list/detail failure must not blank counters. Minimal SPA diff: mainly `DashboardPageComponent`; no new Angular service unless truly needed.
- Update `docs/pages-spec.md` Dashboard description so tiles with the product-page summary card and CI table (below maps) are the documented preview.

### Non-goals

- New health formula or change to `H = min(h_direct, h_ratio)`.
- Extract Product Health microservice (**FM-2**).
- RCA / combo / monitoring coverage (**FM-3**).
- Merge with FM-19; do not change `severityCounts` or counter click → `/console?severity=`.
- Product CRUD, component weights, Sankey, or history on the tile.
- New or changed REST / OpenAPI (`dashboard/summary`, health list/detail). No Liquibase, Docker, adapter, simulator, or `prototype/`.
- Calling unimplemented `GET /api/v1/health/ci/{id}`.
- Empty-snapshot “healing” (do not invent CIs when `configurationItems` is empty).

## Capabilities

### New Capabilities

- None. Dashboard tiles consume the existing health list and product-detail contracts; a second REST capability would duplicate `product-health-graph`.

### Modified Capabilities

- `product-health-graph`: Angular Dashboard `/` SHALL consume the same snapshot list as `/health` (`GET /api/v1/health/products` via `FmApiService.getProducts()`), load per-product CIs via `FmApiService.getProduct(id)` + `buildProfiles`, render tiles **below** event maps with the product-page summary card (`%`, HEALTH_LABELS, damage analysis, min/max) and a CI table (Название КЕ / Тип / Здоровье; not slot codes), color by `percentToLevel`, and navigate to `/health/:id`. REST, formula, snapshot persistence, heatmap, and product graph remain unchanged.

## Impact

- **`frontend/`**: `DashboardPageComponent` (`frontend/src/app/pages/dashboard/dashboard-page.component.ts`) — load products via `FmApiService.getProducts()` and each `getProduct(id)` in addition to `getDashboardSummary()` (summary still drives counters and event maps). Reuse `buildProfiles` / `percentToLevel` / `getHealthPercentColor` from `health-profile.util.ts` as `/health/:id` does. `FmApiService`, `mapProductHealth` / `mapProductHealthDetail`, and models stay as-is.
- **Angular routes:** `/`, `/health`, `/health/:id` unchanged; tile `[routerLink]="['/health', id]"` already exists, only the data source, CI rows, and stacked layout change.
- **`docs/pages-spec.md`**: Dashboard section — stacked layout; tiles with product-page summary card and CI table from product detail + `buildProfiles`; heatmap link retained.
- **Vitest** + **Playwright e2e**. Backend `mvn test` skipped (frontend-only).
- **Не затрагиваются:** `backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, Liquibase, OpenAPI (`docs/fm-module/api.yaml`), Docker, `prototype/`, `demo/gift-shop/`. Hexagonal ADR-001 checklist **N/A** (no backend behavior change).

## Context

Dashboard `/` (`DashboardPageComponent` in `frontend/src/app/pages/dashboard/dashboard-page.component.ts`) is the post-login entry. `ngOnInit` calls `FmApiService.getDashboardSummary()` → `GET /api/v1/dashboard/summary` for FM-19 counters and event maps, and `getProducts()` for tiles. List `components[]` are **slots** (POWER / CPU / HDD / AVAILABILITY), not configuration items. Per-CI names live on `GET /api/v1/health/products/{id}` → `configurationItems` (`fqdn`, `ciType`). Product-page `%` for those CIs is `buildProfiles(configurationItems, activeEvents)` in `health-profile.util.ts` (same as `health-product-page.component.ts`).

Tiles must sit **below** «Карты событий», not in a `1fr 2fr` maps|tiles grid. Gift Shop CIs that must appear on the card: `giftshop-storefront.demo`, `giftshop-catalog.demo`, `giftshop-checkout.demo` (services) and `giftshop-postgres.demo` (database).

Stakeholders: duty engineer and demo presenter. Capability `product-health-graph` is already in main specs. This change is **frontend-only** (`state.modules: ["frontend"]`). No new top-level service. Adapter → ingest is not in this change.

Integration chain:

```text
Angular SPA (DashboardPageComponent)
  ├── GET /api/v1/dashboard/summary          → severityCounts, systemMaps (unchanged, FM-19)
  ├── GET /api/v1/health/products            → tile list (id, name, healthPercent)
  └── GET /api/v1/health/products/{id}       → configurationItems + activeEvents
         FmApiService.getProduct(id)
         buildProfiles(configurationItems, activeEvents)  // same as /health/:id
```

## Goals / Non-Goals

**Goals:**

- Replace `productPreview` on Dashboard with tiles from `GET /api/v1/health/products`: name plus the product-page health-summary aside (`healthPercent`, HEALTH_LABELS, damage, min/max).
- For each product, load `getProduct(id)` and show **all** `configurationItems` in a table (Название КЕ = fqdn, Тип = ciType, Здоровье = `buildProfiles` `%`; services and databases; Gift Shop includes postgres). Keep detail `damagePercent` / `minHealthToday` / `maxHealthToday` / `healthPercent` on the tile. Do not invent CIs; do not label rows with slot codes; do not embed Sankey or RCA.
- Stack layout full width: counters → event maps → product health tiles → «Всего продуктов» (`products.length`). Tiles below maps. Wider tile grid so CI lists fit.
- Color tiles by server product `healthPercent` via existing `percentToLevel` (same mapping as `/health` heatmap), not `maxSeverity`.
- Click tile → `/health/:id`. Keep header link → `/health`.
- Show all products from the health list (no limit 5). Empty list → empty panel. Missing snapshot / no events → same `%` as product page (`buildProfiles`).
- Independent loads: health-list or detail failure must not blank FM-19 counters.
- Leave FM-19 priority counters and event-map links unchanged.
- Update `docs/pages-spec.md` Dashboard description.
- TDD: Vitest then implementation then Playwright e2e. Skip backend `mvn test`.

**Non-Goals:**

- New health formula; FM-2 microservice extract; FM-3 RCA/combo.
- Merge with FM-19; changing `severityCounts` or counter → `/console?severity=`.
- CRUD / weights / Sankey / history on the tile.
- REST / OpenAPI / Liquibase / Docker / adapter / simulator / `prototype/`.
- `GET /api/v1/health/ci/{id}` (unimplemented).
- Empty-snapshot “healing” (do not invent CIs when `configurationItems` is empty).
- New Angular services or routes. State lives on `DashboardPageComponent`.

## Decisions

### D1. Same list API as `/health` — do not extend `dashboard/summary`

Tiles MUST load product id / name / `healthPercent` through existing `FmApiService.getProducts()` (`GET /api/v1/health/products` + `mapProductHealth`). Do **not** add `healthPercent` / CIs to `ProductHealthPreview` / `GET /api/v1/dashboard/summary`. Do **not** synthesize percents from `maxSeverity` or event counts. List `components[]` MUST NOT be used as CI rows (those are slots).

Alternative considered: enrich `productPreview` on the dashboard BFF. Rejected — duplicates the snapshot contract, would be a backend/OpenAPI change (out of scope).

REST impact: **none** (consumer only). SQL / Liquibase: **none**.

### D2. Independent loads — do not block counters on health

Keep `getDashboardSummary()` for `severityCounts`, `totalActive`, and `systemMaps`. Subscribe to `getProducts()` independently so a health-list failure does not blank FM-19 counters. For each list id, subscribe to `getProduct(id)` independently so a single detail failure does not blank other tiles or counters. Stop binding the health panel to `summary.productPreview`.

Do not create a `DashboardHealthService`. All new UI state lives on `DashboardPageComponent`.

### D3. Color by `percentToLevel(healthPercent)`, not `maxSeverity`

Heatmap (`health-page.component.html`): `[attr.data-level]="percentToLevel(p.healthPercent)"`. Dashboard today: `[attr.data-severity]="p.maxSeverity"` plus CSS for critical/major/warning.

Replace severity coloring with the heatmap mapping:

- Import `percentToLevel` (and `getHealthPercentColor` if the tile background should match heatmap squares) from `frontend/src/app/core/health/health-profile.util.ts`.
- Set `data-level` from `percentToLevel(p.healthPercent)` when percent is present; treat missing percent like heatmap (`unknown` / em dash), do not fall back to `maxSeverity`.
- Remove `data-severity` from product tiles so FM-19-era severity CSS does not fight health color.

Do not copy heatmap history mini-strip onto the tile.

### D4. Per-CI rows from `getProduct` + `buildProfiles` — not slots

`GET /api/v1/health/products` `components[]` are slots, not CIs. Per-CI identity comes from `getProduct(id).configurationItems` (`fqdn`, `ciType`). Percents MUST use `buildProfiles(configurationItems, activeEvents)` — the same helper as `health-product-page.component.ts`. Render every returned CI; do not invent missing ones; do not show raw UUIDs as the primary label; do not show POWER / CPU / HDD / AVAILABILITY as slot labels.

Display: summary aside copied from `monq-health-graph` (not the Sankey); then a table — **Название КЕ** (fqdn; not UUID), **Тип** (`ciType` as stored, e.g. service / database), **Здоровье** (`buildProfiles` percent).

### D5. Empty list and missing snapshot are API-faithful

- Empty array from `getProducts()` → keep the «Здоровье продуктов» panel and `/health` header link; product grid has no tiles.
- Missing snapshot / no events → same `%` as `/health/:id` (`buildProfiles`: 100% when there are no active events; empty `configurationItems` → no CI rows). Do not invent Gift Shop FQDNs or slot rows.

### D6. Stacked layout — tiles below event maps

Do **not** use `.panels { grid-template-columns: 1fr 2fr }` for maps|tiles side-by-side. Sections stack: severity counters → «Карты событий» (full width) → «Здоровье продуктов» (full width) → «Всего продуктов» (`products.length`, keep the count after the panels; do not show «Всего активных» / `summary.totalActive`). Product tile grid `minmax` MUST be larger than 140px so CI lists fit.

### D7. Docs only for Dashboard copy; no OpenAPI

Update `docs/pages-spec.md` section Dashboard (`/`): stacked layout; preview is tiles with server product `%` and all CIs from product detail + `buildProfiles`; click → `/health/:id`; header → `/health`. Do not edit `docs/fm-module/api.yaml`.

### D8. Backend / hexagonal — N/A

No new or materially changed backend behavior. **Do not** add ADR-001 hexagonal checklist items or Spring-free use-case tests.

**ADR-001 six-part checklist:**

1. **Use cases and inbound ports** — N/A. Existing `GetProductHealthUseCase.list` / `get` unchanged.
2. **Inbound adapters** — N/A. `ProductHealthController` / `ProductHealthFacade` unchanged.
3. **Outbound ports** — N/A.
4. **Outbound adapter implementations** — N/A.
5. **Infrastructure wiring** — N/A.
6. **Spring-free use-case tests** — N/A. Skip `mvn test` for this change.

Dependency direction: not newly introduced; health hexagonal layout in `ru.wisla.fm.health` is untouched.

## Risks / Trade-offs

- **[FM-19 same page]** Dashboard counters and this panel share `DashboardPageComponent` → keep severity-grid and `getDashboardSummary` wiring intact; Vitest asserts counters still render from summary even if health list/detail fails.
- **[N+1 getProduct]** One detail GET per product on `/`. Acceptable: product count is small; same payload as `/health/:id`; independent subscribe isolates failures.
- **[No snapshot → green 100% CIs]** Same as `/health/:id` `buildProfiles`; do not “heal”. Demo data with Gift Shop CIs remains a demo-data concern, not this change.
- **[Limit 5 removed]** All products on `/` may be denser than preview. Accepted: AC requires full health list; heatmap remains the dedicated overview via header link.
- **Rollback:** revert the Dashboard health panel to `productPreview`; no data written.

## Migration Plan

SPA-only deploy with existing fm-module. No DB migration, no API versioning. Rollback = revert `DashboardPageComponent` (+ `docs/pages-spec.md`).

## Open Questions

None. Layout (tiles below maps), per-CI rows via `getProduct` + `buildProfiles`, and non-goals are closed by the user revision.

## Module changes

### frontend/ (Angular 18 SPA) — in scope

| Touch | Role |
|---|---|
| `frontend/src/app/pages/dashboard/dashboard-page.component.ts` | Independent `getDashboardSummary` / `getProducts` / `getProduct(id)`; `buildProfiles` for CI `%`; stacked layout; tile (summary card + CI table); keep damage/min/max from detail; `percentToLevel`; `[routerLink]="['/health', p.id]"` |
| `frontend/src/app/core/api/fm-api.service.ts` | Unchanged (`getProducts` / `getProduct` already map health APIs) |
| `frontend/src/app/core/health/health-snapshot.mapper.ts` | Unchanged |
| `frontend/src/app/core/health/health-profile.util.ts` | Unchanged (`buildProfiles` / `percentToLevel` reused) |
| `frontend/src/app/pages/health/*` | Unchanged |
| `frontend/tests/unit/dashboard-product-health-tiles.test.ts` | Vitest: mock `getDashboardSummary` + `getProducts` + `getProduct(id)` |
| `frontend/tests/e2e/dashboard-product-health-tiles.spec.ts` | Playwright vs running stack (Gift Shop CIs, tiles below maps) |
| `docs/pages-spec.md` | Dashboard copy |

Routes `/`, `/health`, `/health/:id` stay as registered in `app.routes.ts`.

### backend/fm-module — out of scope

No controller, DTO, Liquibase, or OpenAPI edits. `DashboardService.toProductPreview` may keep serving `productPreview`; Dashboard SPA simply stops using it for the health panel.

### backend/adapter, zabbix-simulator, prototype/, demo/gift-shop/ — out of scope

No Kafka/ClickHouse. No new deployable.

## REST / SQL

| Surface | Impact |
|---|---|
| `GET /api/v1/health/products` | Read-only consumer (id, name, product `healthPercent`) |
| `GET /api/v1/health/products/{id}` | Read-only consumer (`configurationItems`, `activeEvents`) |
| `GET /api/v1/dashboard/summary` | Unchanged; still used for counters and maps |
| `GET /api/v1/health/ci/{id}` | **Must not** be called (unimplemented) |
| OpenAPI `docs/fm-module/api.yaml` | None |
| Liquibase / SQL | None |

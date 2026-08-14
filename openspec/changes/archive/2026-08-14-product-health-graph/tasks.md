## 1. backend/adapter — pull metrics and config sync

- [x] 1.1 Write Spring-free `MetricThresholdEvaluatorTest` covering warning/major/critical/OK bands and `invert` for `up==0` → critical — red
- [x] 1.2 Implement domain `MetricThresholdEvaluator` (no Spring) — green
- [x] 1.3 Write Spring-free `ScrapePullSourcesServiceTest` with fakes: scrape `pull_etl` only, emit on state change, stable `externalId` `{sourceKey}:{ciFqdn}:{metricName}`, no publish when severity unchanged, Kafka via `RawEventPublisherPort` only — red
- [x] 1.4 Declare inbound `ScrapePullSourcesUseCase` and outbound `PrometheusScrapePort`, `PullMetricStateStorePort`; implement `ScrapePullSourcesService` — green for 1.3
- [x] 1.5 Write test that `GET /api/v1/internal/sources` mapping (adapter client) reads `type`, `schedule`, `parserConfig` into `RemoteSourceConfig` / `SourceConfig` — red
- [x] 1.6 Extend adapter `SourceConfig`, `FmModuleSourceConfigClient`, and snapshot mapper to persist `type`, `schedule`, `parserConfig` (fm-module DTO expansion is 2.6) — green for 1.5
- [x] 1.7 Add Liquibase `backend/adapter/src/main/resources/db/changelog/changes/005-pull-etl.sql`: columns `source_type`, `schedule`, `parser_config` on `source_config_snapshots`; table `pull_metric_states`; include in `db.changelog-master.yaml`
- [x] 1.8 Implement JPA `PullMetricStatePersistenceAdapter`, RestClient `PrometheusScrapeAdapter`, `PullEtlScheduler` in `ingest/adapter/in/scheduler`; wire beans in `infrastructure/config`
- [x] 1.9 Run `cd backend/adapter && mvn test` — green; no script/SSH/exec collector

## 2. backend/fm-module — health graph

- [x] 2.1 Write Spring-free `HealthCalculatorTest` for `H = min(h_direct, h_ratio)`, signal→CI map (fatal 0, critical 25, major 50, minor 62, warning 75, none 100), weighted/critical damage, equal-min split, zero weights → 100 — red
- [x] 2.2 Implement `ru.wisla.fm.health.domain.HealthCalculator` — green
- [x] 2.3 Write Spring-free use-case tests (`RecalculateProductHealthServiceTest`, `GetProductHealthServiceTest`, `UpdateProductComponentsServiceTest`) with fake ports: snapshot upsert, history bucket, COMMON bind, PATCH omit components, zero-weight 400, CI in two slots 400 — red
- [x] 2.4 Add Liquibase `backend/fm-module/src/main/resources/db/changelog/changes/013-product-health.sql` (`product_component`, `product_component_ci`, `product_health_snapshot`, `product_health_history`) and include in `db.changelog-master.yaml`; backfill COMMON for existing products
- [x] 2.5 Create hexagonal package layout under `ru.wisla.fm.health` (ports, services, JPA outbound adapters, `HealthConfig`); implement use cases — green for 2.3
- [x] 2.6 Expand `GET /api/v1/internal/sources` with `type`, `schedule`, `parserConfig`; add controller/service tests — red then green
- [x] 2.7 Expand `GET /api/v1/health/products` and `GET /api/v1/health/products/{id}` with `healthPercent`, `damagePercent`, components, `sankey`; add `GET /api/v1/health/products/{id}/history`; keep `maxSeverity`, `activeEventCount`, `ciIds`; 401 without JWT — tests then impl
- [x] 2.8 Expand `PATCH /api/v1/admin/products/{id}` with optional `components`; auto-bind new `ciIds` to COMMON; 403 non-admin — tests then impl
- [x] 2.9 Publish in-process `EventCreated`/`EventUpdated`/`EventClosed` from processing adapter layer; `EventLifecycleHealthListener` + 5 min `HealthRecalcScheduler`; copy snapshot onto `products.max_severity` / `active_event_count`
- [x] 2.10 Add `ru.wisla.fm.health..` to `HexagonalArchitectureTest` `IN_SCOPE`; write/adjust ArchUnit so domain/application stay Spring-free
- [x] 2.11 Run `cd backend/fm-module && mvn test` — green

## 3. frontend — Vitest then UI

- [x] 3.1 Write Vitest tests for health DTO mapping (percents, components, sankey links, history buckets) and assert `health-profile.util` no longer builds fake `defaultComponents` / synthetic timeline — red
- [x] 3.2 Update API models and `FmApiService` for snapshot/history/PATCH `components`; strip fake generation from `health-profile.util.ts` (keep labels/colors only) — green
- [x] 3.3 Render `/health` heatmap from `healthPercent`; `/health/:productId` components, damage, Sankey (`d3-sankey` wrapper), history heatmap, admin weight editor
- [x] 3.4 Run `cd frontend && npm test` — green

## 4. demo/gift-shop overlay

- [x] 4.1 Create `demo/gift-shop/` overlay compose: storefront :8091, catalog :8092, checkout :8093, postgres host :5433, optional cadvisor host :8088; README with `docker compose -f backend/docker-compose.yaml -f demo/gift-shop/docker-compose.yaml up -d --build`
- [x] 4.2 Implement apps with storefront/catalog/checkout, `GET /metrics`, chaos `POST /chaos/cpu|latency|errors|disk|down|reset`; no ingest/Kafka clients
- [x] 4.3 Add seed (SQL/JSON) for product `giftshop`, CIs, POWER/CPU/HDD/AVAILABILITY components, and `pull_etl` source targeting app `/metrics`
- [x] 4.4 Confirm overlay is not a `backend/` Maven module and base compose still starts without gift-shop

## 5. OpenAPI and docs

- [x] 5.1 Update `docs/fm-module/api.yaml` for health list/detail/history, admin `components`, internal sources extra fields
- [x] 5.2 Update `docs/fm-module/db.md` with the four health tables; adapter db notes for `pull_metric_states` and snapshot columns
- [x] 5.3 Update `docs/pages-spec.md` (`/health`, `/health/:productId` Sankey/heatmap/weights) and `docs/architecture.md` integration (pull → Kafka, health BC in fm-module) without changing ingest topology

## 6. Playwright e2e (backend up)

- [x] 6.1 Add Playwright spec: authenticated `/health` shows `healthPercent` from API (not client-fake components)
- [x] 6.2 Add Playwright spec: admin PATCHes component weight on `/health/:productId` and sees it after reload
- [ ] 6.3 Optional tag: with overlay up, chaos on checkout → adapter event → product healthPercent drops
- [x] 6.4 Run `cd frontend && npm run test:e2e` against running backend (`http://localhost:8080`) — green

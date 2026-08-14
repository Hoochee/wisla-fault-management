## Why

Сейчас здоровье продукта в WISLA — оболочка: `GET /api/v1/health/products` отдаёт только `maxSeverity` и `activeEventCount` по связанным КЕ, а проценты, компоненты POWER/CPU/HDD и timeline синтезирует Angular (`health-profile.util.ts`). Нет Monq-формулы, весов, урона и Sankey с сервера; тип источника `pull_etl` есть в enum, но adapter не скрейпит `/metrics`. Нужен демо-продукт Gift Shop, чтобы прогнать граф здоровья на реальных сигналах через adapter, не через клиентские заглушки.

## What Changes

- Серверный расчёт здоровья продукта (Monq, без combo) в bounded context `ru.wisla.fm.health` внутри `backend/fm-module`: % / damage / веса компонентов / Sankey / история heatmap. Health **не** выделяется в отдельный микросервис (follow-up в `BACKLOG.md`).
- Angular `/health` и `/health/:productId` читают снимки с API; клиентская генерация фейковых компонентов и timeline в `health-profile.util.ts` прекращается.
- Admin PATCH состава компонентов и весов (`PATCH /api/v1/admin/products/{id}` с `components`); существующий CRUD продуктов и `ciIds` сохраняется. Новые КЕ из `ciIds` автоматически попадают в компонент `COMMON`.
- Adapter реализует HTTP pull Prometheus `/metrics` для `type=pull_etl`: пороги → PROBLEM/OK события только при смене состояния, публикация в существующий Kafka `fm.raw-events`. Скрипты/SSH/exec не добавляются.
- Синхронизация конфига источника расширяется полями `type`, `schedule`, `parserConfig` (`GET /api/v1/internal/sources`).
- Демо-оверлей `demo/gift-shop/` (storefront, catalog, checkout, postgres, optional cadvisor) с `/metrics` и chaos HTTP; мониторится **только** через adapter. Не Maven-модуль FM.

### Non-goals

- Выделение `health-service` (отложено в `BACKLOG.md`).
- RCA, combo-влияние, coverage % / покрытие мониторингом.
- Скриптовый collector в adapter; push из gift-shop в fm-module или Kafka.
- ClickHouse / хранение timeseries метрик в FM.
- Изменения `prototype/`.
- Правки specs `kafka-raw-event-ingest`, `rules-*`, `console-*`, `architecture-*` (расширение ArchUnit на пакет health — требование внутри `product-health-graph`, не delta `architecture-enforcement-archunit`).
- Смена Kafka envelope / topic `fm.raw-events`.
- Удаление колонок `products.max_severity` / `active_event_count`.
- Полноценная РСМ КЕ↔КЕ.

## Capabilities

### New Capabilities

- `product-health-graph`: серверный расчёт здоровья по формуле Monq (`H = min(h_direct, h_ratio)`), сущности компонентов/снимков/истории, REST `/api/v1/health/*`, Sankey и heatmap с API, hexagonal пакет `ru.wisla.fm.health` с ArchUnit; Angular перестаёт подменять данные.
- `adapter-pull-metrics`: HTTP scrape Prometheus `/metrics` для `pull_etl`, оценка порогов, эмит PROBLEM/OK только при смене состояния, стабильный `externalId`, таблица `pull_metric_states`; публикация через существующий `RawEventPublisherPort` → `fm.raw-events`.
- `gift-shop-demo`: overlay `demo/gift-shop/` (витрина, каталог, checkout, postgres, optional cadvisor), app `/metrics` и chaos HTTP; продукт Gift Shop сидится в FM и наблюдается только через adapter.

### Modified Capabilities

- `health-product-crud`: ADDED admin PATCH компонентов/весов и автопривязка новых КЕ из `ciIds` к `COMMON`. Существующие сценарии CRUD продуктов и `product_ci` не меняются.
- `adapter-config-sync`: ADDED синхронизация `type`, `schedule`, `parserConfig` для источников `pull_etl` через `GET /api/v1/internal/sources` и snapshot adapter.

## Impact

- **`backend/fm-module`**: hexagonal BC `ru.wisla.fm.health`; Liquibase `013-product-health.sql` (`product_component`, `product_component_ci`, `product_health_snapshot`, `product_health_history`); REST `GET /api/v1/health/products`, `GET /api/v1/health/products/{id}`, `GET /api/v1/health/products/{id}/history`; admin `PATCH /api/v1/admin/products/{id}` + `components`; `GET /api/v1/internal/sources` + `type`/`schedule`/`parserConfig`; ArchUnit IN_SCOPE += `ru.wisla.fm.health..`.
- **`backend/adapter`**: `ScrapePullSourcesUseCase`, `PullEtlScheduler`, `MetricThresholdEvaluator`; Liquibase колонки `source_type`, `schedule`, `parser_config` в `source_config_snapshots` + таблица `pull_metric_states` (БД `wisla_fm_adapter`). Порт adapter **8081**.
- **`frontend/`**: маршруты `/health`, `/health/:productId` без смены URL; модели снимка, Sankey, heatmap, редактор весов; Vitest + Playwright e2e (backend up).
- **`demo/gift-shop/`**: overlay compose (storefront **8091**, catalog **8092**, checkout **8093**, postgres host **5433**, cadvisor host **8088** best-effort).
- **`docs/`**: OpenAPI `docs/fm-module/api.yaml`, `docs/fm-module/db.md`, `docs/pages-spec.md`, `docs/architecture.md` (без смены topology ingest).
- **Не затрагиваются:** `backend/zabbix-simulator`, `prototype/`, Kafka topic/envelope, processing rules.

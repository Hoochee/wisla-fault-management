## Context

Сейчас `GET /api/v1/health/products` в `backend/fm-module` отдаёт только `maxSeverity` и `activeEventCount` по активным событиям связанных КЕ. Проценты, выдуманные компоненты CPU/HDD и timeline строит Angular в `frontend/src/app/core/health/health-profile.util.ts`. Колонки `products.max_severity` / `active_event_count` не источник истины. Adapter умеет только push webhook → Kafka `fm.raw-events`; `pull_etl` есть в `event_sources.type`, но scrape не реализован. `GET /api/v1/internal/sources` отдаёт `sourceId`, `sourceKey`, `apiKeyHash`, `status`, `filterRules` — без `type`/`schedule`/`parserConfig`. Processing не публикует in-process lifecycle-события. Демо-продукта с `/metrics` нет.

Утверждённые deployable: `adapter` (:8081), `fm-module` (:8080), `zabbix-simulator` (:8082). Новый FM-микросервис в этом change запрещён. ADR-001 обязателен для нового поведения в `ru.wisla.fm.health` и adapter pull.

## Goals / Non-Goals

**Goals:**

- Серверный расчёт здоровья продукта по формуле Monq (без combo) и отдача снимка/Sankey/истории через `/api/v1/health/*`.
- Настраиваемые компоненты и веса на экземпляре продукта; Angular `/health` и `/health/:productId` рисуют только серверные данные.
- Adapter HTTP pull Prometheus `/metrics` для `pull_etl` → PROBLEM/OK в существующий Kafka `fm.raw-events`.
- Демо overlay Gift Shop с `/metrics` и chaos HTTP, наблюдаемый только через adapter.
- Hexagonal швы `ru.wisla.fm.health` и pull-use-case в adapter по ADR-001, чтобы позже скопировать пакет в сервис (`BACKLOG.md`).

**Non-Goals:**

- Выделение `health-service`; RCA; combo; coverage %.
- Скрипты/SSH/exec в adapter; push из gift-shop в FM.
- ClickHouse, timeseries метрик в FM, правки `prototype/`.
- Смена Kafka topic/envelope; удаление `products.max_severity`.
- Массовый hexagonal остальных контекстов; delta-specs `kafka-raw-event-ingest`, `rules-*`, `console-*`, `architecture-*`.

## Decisions

### D1. Health остаётся bounded context в fm-module

Расчёт живёт в `ru.wisla.fm.health`, БД `wisla_fm`. SPA по-прежнему ходит на `:8080 /api/v1/health/*`. Выделение сервиса — пункт `BACKLOG.md` `health-service-extract`, не этот change.

Альтернатива: новый deployable `health-service` сейчас. Отклонена: вторая БД, JWT, compose и рассинхрон снимка при ещё не зафиксированной формуле; AGENTS.md запрещает invent top-level FM services.

### D2. Формула Monq без combo

Иерархия: сигнал (`Event`) → КЕ → `ProductComponent` → продукт.

Сигнал → здоровье КЕ (наихудший открытый; `closed`/`archived` не считаются):

| Severity | CI health |
|---|---|
| fatal | 0 |
| critical | 25 |
| major | 50 |
| minor | 62 |
| warning | 75 |
| нет открытых | 100 |

Компонент и продукт:

- `h_direct = min(h_i)` по связям `influence_type=critical`, у которых `h_i < critical_threshold` (default **100**).
- `h_ratio = Σ k_i * h_i`, `k_i = weight_i / Σ weights` по всем связям.
- `H = min(h_direct, h_ratio)`. Нет critical → `H = h_ratio`. Нет весов > 0 → `H = h_direct` либо 100.

Урон (ширина Sankey):

- weighted: `(100 - h) * k`
- critical: `(100 - h)`
- несколько одинаковых минимумов: урон делится поровну.

`HealthCalculator` — чистая функция `(topology, ciHealthMap) → Snapshot`, без Spring.

Альтернатива: combo «k из n». Отклонена (parked в беклоге). Покрытие мониторингом в формулу не входит.

### D3. Модель и таблицы

Владение: CMDB — `products`, `product_ci`; health — компоненты и снимки.

| Сущность | Таблица | Смысл |
|---|---|---|
| `ProductComponent` | `product_component` | слот POWER/CPU/HDD/AVAILABILITY/COMMON |
| `ProductComponentCi` | `product_component_ci` | КЕ влияет ровно на один компонент данного продукта |
| `ProductHealthSnapshot` | `product_health_snapshot` | текущие % / damage / payload (components, signals, sankey) |
| `ProductHealthHistory` | `product_health_history` | бакеты heatmap (худший статус в ячейке) |

При создании продукта, если компонентов нет — создаётся `COMMON` (weight=100, `influence_type=weighted`). PATCH `ciIds` по-прежнему заменяет `product_ci`; новые КЕ без слота кладутся в `COMMON`. `products.max_severity` / `active_event_count` обновляются из snapshot при пересчёте (колонки не удаляем).

Альтернатива: считать health на лету без снимков. Отклонена: UI heatmap/Sankey и будущий extract сервиса требуют persisted read-model.

### D4. ADR-001 hexagonal — fm-module health и adapter pull

Направление зависимостей: `domain` ← `application` (ports + services) ← `adapter` / `infrastructure`. Domain и application **не** импортируют Spring, JPA, Jackson, Kafka, HTTP.

**fm-module `ru.wisla.fm.health` — шестичастный чеклист:**

1. **Use cases / inbound ports:** `RecalculateProductHealthUseCase`, `GetProductHealthUseCase`, `GetProductHealthHistoryUseCase`, `UpdateProductComponentsUseCase`.
2. **Inbound adapters:** `ProductHealthController` (`adapter/in/web`); admin PATCH компонентов в существующем admin web-слое, делегирующий в `UpdateProductComponentsUseCase`; `EventLifecycleHealthListener` на in-process `EventCreated` / `EventUpdated` / `EventClosed`; `HealthRecalcScheduler` как страховка.
3. **Outbound ports:** `ProductTopologyPort`, `ActiveSignalsPort`, `HealthSnapshotStorePort`, `HealthHistoryStorePort`, `ProductAggregateWritePort`.
4. **Outbound adapters:** JPA на новые таблицы; `ActiveSignalsPort` читает processing **только** из `adapter/out` (не из domain/application напрямую к `EventJpaRepository`).
5. **Infrastructure:** `HealthConfig` в `health/infrastructure/config`.
6. **Spring-free tests:** `HealthCalculatorTest`; use-case тесты с fake ports.

ArchUnit: добавить `ru.wisla.fm.health..` в `IN_SCOPE` `HexagonalArchitectureTest` (не трогая spec `architecture-enforcement-archunit`). Текущий `ProductHealthService` в `health.api` заменить тонким контроллером + use cases.

**adapter pull — чеклист:**

1. **Use cases:** `ScrapePullSourcesUseCase`.
2. **Inbound:** `PullEtlScheduler` в `ingest/adapter/in/scheduler`.
3. **Outbound ports:** `PrometheusScrapePort`, `PullMetricStateStorePort`; публикация через существующий `RawEventPublisherPort`.
4. **Outbound adapters:** RestClient scrape; JPA `pull_metric_states`; Kafka publisher без изменений контракта.
5. **Infrastructure:** wiring в существующем `ingest/infrastructure/config`.
6. **Spring-free tests:** `MetricThresholdEvaluatorTest`; `ScrapePullSourcesServiceTest` с fakes.

Альтернатива: pull как отдельный BC `com.wisla.fm.adapter.pull`. Отклонена: это тот же ingest (filter + Kafka envelope); расширение `ingest` меньше швов.

### D5. Adapter: HTTP pull Prometheus, не скрипты

`type=pull_etl` — scheduler по `schedule` (`30s` или cron). Scrape `parserConfig.targets[].url`, пороги в `parserConfig.rules`. Эмит **только при смене состояния**. Стабильный `externalId` = `{sourceKey}:{ciFqdn}:{metricName}`. Recovery: событие со `status=ok` (существующий problem-resolution). `push_rest` scheduler игнорирует. Pull **не** ходит в `POST /api/v1/ingest`.

Таблица adapter `pull_metric_states (source_id, external_id, last_severity, last_value, updated_at)` PK `(source_id, external_id)`.

`GET /api/v1/internal/sources` и `source_config_snapshots` получают `type` (`source_type`), `schedule`, `parserConfig` (`parser_config` jsonb). API-key для исходящего scrape не нужен.

Альтернатива: скрипты/SSH/docker exec. Отклонена (RCE, Windows, нет идемпотентности). Push из gift-shop в fm-module нарушает ingest-only-through-adapter.

### D6. Demo Gift Shop — overlay, не Maven-модуль FM

Каталог `demo/gift-shop/`. Запуск:

```bash
docker compose -f backend/docker-compose.yaml -f demo/gift-shop/docker-compose.yaml up -d --build
```

| Сервис | Порт | Роль |
|---|---|---|
| `giftshop-storefront` | 8091 | витрина, корзина UI |
| `giftshop-catalog` | 8092 | API каталога |
| `giftshop-checkout` | 8093 | checkout / mock-оплата |
| `giftshop-postgres` | 5433 host | БД `giftshop`, не `wisla_fm` |
| `giftshop-cadvisor` | 8088 host | runtime metrics, **best-effort** на Windows |

Каждый app обязан отдавать Prometheus `/metrics`. Chaos: `POST /chaos/cpu|latency|errors|disk|down|reset`. Adapter резолвит DNS в общей сети compose. cAdvisor не блокирует приёмку: обязательный путь — app `/metrics`.

Seed продукта `Gift Shop` (`code=giftshop`): КЕ + компоненты POWER/CPU/HDD/AVAILABILITY + источник `pull_etl`.

Альтернатива: модуль в `backend/`. Отклонена — gift-shop не FM-сервис.

### D7. UI

Маршруты без смены: `/health`, `/health/:productId`. Данные Sankey: `sankey: { nodes, links: [{from, to, damage}] }` в detail DTO. `health-profile.util.ts` — только цвета/labels. Библиотека Sankey: **d3-sankey** (узкий Angular wrapper); ECharts в бандле нет.

Heatmap: `GET /api/v1/health/products/{id}/history?from&to&bucketMinutes=` (default bucket **15** мин). Редактор весов — admin на карточке продукта.

### Module split

| Модуль | Что меняется |
|---|---|
| `backend/fm-module` | пакет `ru.wisla.fm.health` по ADR-001; Liquibase `013-product-health.sql`; REST health/admin/internal; ArchUnit IN_SCOPE; processing публикует in-process lifecycle events из своего adapter-слоя (без смены Kafka ingest) |
| `backend/adapter` | pull use case + scheduler; Liquibase `005-pull-etl.sql`; расширение `SourceConfig` / sync |
| `frontend/` | модели снимка, Sankey, heatmap, редактор весов; Vitest; Playwright e2e |
| `demo/gift-shop/` | overlay compose, apps, chaos, README, seed |
| `docs/` | OpenAPI, db.md, pages-spec, architecture (без смены topology) |

`backend/zabbix-simulator` и `prototype/` не трогаем.

### Integration points

```text
SPA ──REST JWT──► fm-module :8080  /api/v1/health/*  /api/v1/admin/products
gift-shop /metrics ──HTTP GET──► adapter :8081 PullEtlScheduler
adapter ──Kafka fm.raw-events──► fm-module ingestion consumer
fm-module ──GET /api/v1/internal/sources (X-Service-Key)──► adapter sync
processing EventCreated/Updated/Closed (in-process) ──► health RecalculateProductHealth
```

Webhook-путь, topic `fm.raw-events`, `RawEventEnvelope.schemaVersion = 1` не меняются.

### Liquibase

**fm-module** `backend/fm-module/src/main/resources/db/changelog/changes/013-product-health.sql` (include в `db.changelog-master.yaml`):

```sql
product_component (
  id UUID PK, product_id FK products ON DELETE CASCADE,
  code VARCHAR(32) NOT NULL, name VARCHAR(128) NOT NULL,
  weight INT NOT NULL CHECK (weight BETWEEN 0 AND 100),
  influence_type VARCHAR(16) NOT NULL CHECK (influence_type IN ('weighted','critical')),
  critical_threshold INT NOT NULL DEFAULT 100,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (product_id, code)
)
product_component_ci (
  component_id UUID NOT NULL FK product_component(id) ON DELETE CASCADE,
  ci_id UUID NOT NULL FK configuration_items(id) ON DELETE CASCADE,
  weight INT CHECK (weight IS NULL OR weight BETWEEN 0 AND 100),
  PRIMARY KEY (component_id, ci_id)
)
product_health_snapshot (
  product_id UUID PK FK products(id) ON DELETE CASCADE,
  health_percent INT NOT NULL,
  damage_percent INT NOT NULL,
  max_severity VARCHAR(16) NOT NULL,
  active_event_count INT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}',
  calculated_at TIMESTAMPTZ NOT NULL
)
product_health_history (
  id UUID PK,
  product_id UUID NOT NULL FK products(id) ON DELETE CASCADE,
  bucket_start TIMESTAMPTZ NOT NULL,
  bucket_minutes INT NOT NULL,
  min_health INT NOT NULL,
  max_health INT NOT NULL,
  worst_severity VARCHAR(16) NOT NULL,
  UNIQUE (product_id, bucket_start)
)
```

UNIQUE `(product_id, ci_id)` на влиянии: одна КЕ — один компонент продукта (через составной уникальный индекс на join или сервисная инварианта + частичный unique через таблицу). Инвариант: КЕ продукта не может висеть на двух `product_component` одного `product_id`.

**adapter** `005-pull-etl.sql`:

- `source_config_snapshots`: `source_type VARCHAR(32) NOT NULL DEFAULT 'push_rest'`, `schedule VARCHAR(64)`, `parser_config JSONB NOT NULL DEFAULT '{}'`.
- `pull_metric_states (source_id UUID NOT NULL, external_id VARCHAR(512) NOT NULL, last_severity VARCHAR(16), last_value DOUBLE PRECISION, updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (source_id, external_id))`.

### REST

| Метод | Путь | Контракт |
|---|---|---|
| GET | `/api/v1/health/products` | как сейчас + `healthPercent`, `damagePercent`, `components[]` (code, healthPercent, damagePercent). Поля `maxSeverity`, `activeEventCount`, `ciIds` сохраняются |
| GET | `/api/v1/health/products/{id}` | snapshot: components, signals, `sankey`, min/max today, CIs |
| GET | `/api/v1/health/products/{id}/history?from&to&bucketMinutes=` | бакеты heatmap; default `bucketMinutes=15` |
| PATCH | `/api/v1/admin/products/{id}` | + опциональный `components: [{code, name, weight, influenceType, criticalThreshold, ciIds}]`; omit → состав слотов не меняется |
| GET | `/api/v1/internal/sources` | + `type`, `schedule`, `parserConfig` |

401 без JWT на `/api/v1/health/*` и admin; 403 на write не-админу; internal — `X-Service-Key`.

Пересчёт: на lifecycle событиях затронутых продуктов (через `product_ci`) + полный recalc каждые 5 мин.

## Risks / Trade-offs

- [Шторм событий со scrape] → эмит только при смене состояния; стабильный `externalId`; существующий dedup.
- [Sync без parserConfig] → расширить internal API и snapshot в том же инкременте.
- [cAdvisor на Windows] → app `/metrics` обязателен; cadvisor optional.
- [Двойной источник `max_severity`] → писать из snapshot при каждом recalc.
- [Processing не эмитит lifecycle] → явная задача: in-process события из processing adapter; scheduler 5 мин как страховка.
- [Путаница `product_ci` vs `product_component_ci`] → PATCH `ciIds` кладёт новые КЕ в COMMON; UI состава показывает слот.
- [Все веса = 0] → `h_ratio` трактовать как 100; PATCH отвергает, если ни один weight > 0 (400).
- [E2e без overlay] → Playwright: основной сценарий API-контракт на `/health`; chaos→падение % — tag при поднятом overlay.

## Migration Plan

1. Liquibase adapter `005-pull-etl.sql`, затем fm-module `013-product-health.sql`. Существующие продукты получают `COMMON` backfill.
2. Расширить `GET /api/v1/internal/sources` (обратная совместимость: новые поля additive). Adapter игнорирует `type` ≠ `pull_etl`.
3. Health REST additive; старые клиенты читают прежние поля.
4. Фронт переключается на серверный снимок одним релизом со backend (иначе Sankey пустой).
5. Overlay gift-shop опционален для основного стека; основной `backend/docker-compose.yaml` не требует gift-shop.
6. Rollback: down Liquibase не делаем автоматически; фича-флаг не нужен — UI деградирует к `maxSeverity`, если `healthPercent` отсутствует. Откат кода: удалить pull scheduler и health use cases; таблицы можно оставить.

## Open Questions

Нет блокирующих. Зафиксированные умолчания: bucket 15 мин; Sankey = d3-sankey; COMMON auto-create; cadvisor best-effort; CI-level `product_component_ci.weight` NULL → вес компонента.

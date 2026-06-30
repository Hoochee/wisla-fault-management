# WISLA FM Adapter — Database Schema

**Database:** `wisla_fm_adapter`  
**СУБД:** PostgreSQL 15+  
**Сервис:** `backend/adapter/` (порт 8081)

## Соглашения

- UUID primary keys (`gen_random_uuid()`)
- `snake_case` для таблиц и колонок
- `created_at`, `updated_at` TIMESTAMPTZ на всех таблицах
- Кросс-сервисные ссылки (`source_id`) — **логические UUID** без FK на `wisla_fm`

---

## Таблицы

### `buffered_messages`

Очередь сообщений при недоступности fm-module. Фоновый worker выбирает записи с `next_retry_at <= now()` и повторяет `POST /api/v1/ingest`.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, DEFAULT `gen_random_uuid()` | Идентификатор буферной записи |
| source_id | UUID | NOT NULL | Логическая ссылка на `event_sources.id` в fm-module |
| payload | JSONB | NOT NULL | Исходный JSON события (как получен от webhook) |
| retry_count | INTEGER | NOT NULL DEFAULT 0 | Число неудачных попыток доставки |
| next_retry_at | TIMESTAMPTZ | NOT NULL | Время следующей попытки (exponential backoff) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Момент постановки в буфер |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Последнее изменение (retry, успешная доставка) |

**Индексы:**

| Name | Columns | Purpose |
|------|---------|---------|
| `idx_buffered_messages_next_retry` | `(next_retry_at)` WHERE `next_retry_at IS NOT NULL` | Выборка записей для retry worker |
| `idx_buffered_messages_source_id` | `(source_id)` | Изоляция и мониторинг по источнику |

---

### `source_config_snapshots`

Локальный кэш конфигурации источника. Заполняется pull/push из fm-module (`SourceConfigChanged`). Используется для предфильтрации и проверки `X-Source-Key` без обращения к модулю на каждый webhook.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| source_id | UUID | PK | Логическая ссылка на `event_sources.id` в fm-module |
| filter_rules | JSONB | NOT NULL DEFAULT `'{}'` | Правила предобработки (копия из configuration context) |
| api_key_hash | VARCHAR(255) | NOT NULL | Хэш API-ключа источника для аутентификации webhook |
| endpoint | VARCHAR(512) | NOT NULL | Базовый URL fm-module для исходящего ingest |
| ttl_expires_at | TIMESTAMPTZ | NOT NULL | Истечение кэша; после — повторный pull конфигурации |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Первое сохранение снимка |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Последнее обновление снимка |

**Дополнительные колонки (рекомендуемые для runtime):**

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| source_key | VARCHAR(128) | NOT NULL | Публичный ключ для маршрута `/webhook/{sourceKey}` |
| blocked | BOOLEAN | NOT NULL DEFAULT FALSE | Блокировка ingress при шторме (`SourceBlocked`) |

**Индексы:**

| Name | Columns | Purpose |
|------|---------|---------|
| `idx_source_config_snapshots_source_key` | `(source_key)` UNIQUE | Резолв sourceKey → source_id на webhook |
| `idx_source_config_snapshots_ttl` | `(ttl_expires_at)` | Инвалидация просроченных снимков |

---

### `adapter_heartbeats`

Журнал исходящих heartbeat-сообщений адаптера в fm-module (локальный audit). Обновление `EventSource.last_success_at` выполняется на стороне модуля.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, DEFAULT `gen_random_uuid()` | Идентификатор записи heartbeat |
| source_id | UUID | NOT NULL | Логическая ссылка на `event_sources.id` в fm-module |
| sent_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Момент отправки heartbeat в fm-module |
| status | VARCHAR(32) | NOT NULL | Результат доставки (`success`, `failed`, `buffered`) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Момент создания записи |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Обновление статуса при повторной обработке |

**Индексы:**

| Name | Columns | Purpose |
|------|---------|---------|
| `idx_adapter_heartbeats_source_sent` | `(source_id, sent_at DESC)` | История heartbeat по источнику |

---

## Миграции

1. `001_init.sql` — `buffered_messages`, `source_config_snapshots`, `adapter_heartbeats`
2. `002_buffered_messages_retry_index.sql` — partial index на `next_retry_at`
3. `003_source_config_source_key.sql` — `source_key`, unique index (если не в 001)

---

## Кросс-сервисные ссылки

| Поле | Сервис-владелец | Описание |
|------|-----------------|----------|
| `source_id` | fm-module (`event_sources.id`) | Логическая UUID-ссылка; FK **не создаётся** |
| `endpoint` | fm-module | Базовый URL BFF (`http://fm-module:8080` в Docker Compose) |
| `filter_rules` | fm-module (`event_sources.filter_rules`) | Денормализованная копия для предфильтрации на адаптере |
| `api_key_hash` | fm-module (`event_sources` credentials) | Хэш для проверки входящего `X-Source-Key` / `sourceKey` |

---

## Связь с API

| API endpoint | Таблица | Операция |
|--------------|---------|----------|
| `POST /webhook/{sourceKey}` | `source_config_snapshots` | Чтение кэша, проверка ключа и `filter_rules` |
| `POST /webhook/{sourceKey}` | `buffered_messages` | INSERT при недоступности fm-module |
| `GET /internal/sources/{sourceId}/config` | `source_config_snapshots` | SELECT по `source_id` |
| `POST /internal/probe` | `source_config_snapshots` | Чтение конфигурации; опционально INSERT в `buffered_messages` |
| Heartbeat worker | `adapter_heartbeats` | INSERT после каждой отправки |
| Retry worker | `buffered_messages` | SELECT/UPDATE/DELETE при успешной доставке |

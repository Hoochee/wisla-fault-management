# fm-module — Database Schema

**Database:** `wisla_fm`  
**СУБД:** PostgreSQL 15+  
**Сервис:** `backend/fm-module/` (BFF, ingestion, processing, все bounded contexts)

## Conventions

- UUID primary keys: `gen_random_uuid()` (extension `pgcrypto` or `uuid-ossp`)
- `snake_case` for tables and columns
- `created_at`, `updated_at` TIMESTAMPTZ NOT NULL DEFAULT `NOW()` on all mutable tables
- Enum-like columns stored as `VARCHAR` with CHECK constraints (Liquibase-friendly)
- Cross-service references (`adapter`) — logical UUID only, **no FK** outside `wisla_fm`
- JSON API uses **camelCase**; DB uses **snake_case** (Spring/Jackson mapping)
- Soft deletes not used in MVP; `DELETE` is hard delete with referential guards in service layer

## Extensions

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
```

---

## Entity relationship overview

```
users ──┬── user_roles ── roles
        └── event_maps (owner_user_id)

event_sources ──┬── raw_events
                └── events

configuration_items ──┬── product_ci ── products
                      └── events (ci_id, denormalized snapshots)

events ──┬── event_action_logs
         └── events (root_event_id self-ref)

processing_rules ── rule_versions

module_settings (singleton row)

ci_health_snapshots (optional read-model, ci_id FK)
```

---

## Tables

### `users`

Identity aggregate root. Local auth MVP; `external_id` reserved for AD (post-MVP).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| login | VARCHAR(128) | NOT NULL UNIQUE | Username |
| password_hash | VARCHAR(255) | NOT NULL | bcrypt/argon2 |
| full_name | VARCHAR(255) | NOT NULL | Display name |
| email | VARCHAR(255) | NOT NULL UNIQUE | |
| team | VARCHAR(128) | | NOC team / group |
| active | BOOLEAN | NOT NULL DEFAULT TRUE | |
| external_id | VARCHAR(255) | UNIQUE | AD object GUID (post-MVP) |
| preferences | JSONB | NOT NULL DEFAULT '{}' | Sidebar, column layouts (`UserPreferences`) |
| last_login_at | TIMESTAMPTZ | | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `idx_users_login`, `idx_users_active`, `idx_users_team`

---

### `roles`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| name | VARCHAR(128) | NOT NULL UNIQUE | e.g. Администратор |
| description | TEXT | | |
| permissions | JSONB | NOT NULL DEFAULT '[]' | Array of permission codes |
| system_role | BOOLEAN | NOT NULL DEFAULT FALSE | Built-in roles cannot be deleted |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

---

### `user_roles`

M:N users ↔ roles.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| user_id | UUID | PK, FK → users(id) ON DELETE CASCADE | |
| role_id | UUID | PK, FK → roles(id) ON DELETE CASCADE | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `idx_user_roles_role_id`

---

### `event_sources`

Configuration aggregate — adapter endpoints, API keys, filters.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| name | VARCHAR(255) | NOT NULL | |
| type | VARCHAR(32) | NOT NULL | `push_rest`, `push_snmp_trap`, `pull_etl` |
| protocol | VARCHAR(64) | NOT NULL | e.g. HTTPS/REST |
| endpoint | VARCHAR(512) | NOT NULL | Adapter-facing endpoint |
| api_key_hash | VARCHAR(255) | NOT NULL | Hashed source key |
| api_key_prefix | VARCHAR(16) | NOT NULL | Masked display prefix |
| status | VARCHAR(16) | NOT NULL DEFAULT 'inactive' | `active`, `inactive`, `blocked` |
| schedule | VARCHAR(64) | | CRON for pull_etl |
| filter_rules | JSONB | NOT NULL DEFAULT '{}' | Pre-processing filters |
| parser_config | JSONB | NOT NULL DEFAULT '{}' | JSON parser mapping |
| adapter_version | VARCHAR(32) | | Last reported adapter version |
| last_success_at | TIMESTAMPTZ | | Last ingest/heartbeat |
| webhook_path_key | VARCHAR(64) | UNIQUE | Public webhook key segment |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**CHECK:** `type IN ('push_rest','push_snmp_trap','pull_etl')`  
**CHECK:** `status IN ('active','inactive','blocked')`

**Indexes:** `idx_event_sources_status`, `idx_event_sources_type`, `idx_event_sources_last_success_at`

---

### `configuration_items`

CMDB aggregate root.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| fqdn | VARCHAR(512) | NOT NULL UNIQUE | Node FQDN |
| ci_type | VARCHAR(64) | NOT NULL | узел / оборудование / сервис |
| system_name | VARCHAR(255) | NOT NULL | ИС name |
| subsystem_name | VARCHAR(255) | | |
| software | VARCHAR(255) | | |
| tags | JSONB | NOT NULL DEFAULT '[]' | e.g. tenant:moscow |
| external_ids | JSONB | NOT NULL DEFAULT '{}' | WISLA / ITSM ids |
| auto_created | BOOLEAN | NOT NULL DEFAULT FALSE | Created on ingest by FQDN |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `idx_configuration_items_fqdn`, `idx_configuration_items_system_name`, GIN `idx_configuration_items_tags` ON `tags`

---

### `products`

Product health read-model root (denormalized counters updated by processing/health contexts).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| code | VARCHAR(64) | UNIQUE | Legacy/slug id e.g. prod-billing |
| name | VARCHAR(255) | NOT NULL | |
| tenant | VARCHAR(128) | NOT NULL | |
| site | VARCHAR(128) | NOT NULL | |
| tags | JSONB | NOT NULL DEFAULT '[]' | |
| max_severity | VARCHAR(16) | NOT NULL DEFAULT 'normal' | Denormalized from active events |
| active_event_count | INTEGER | NOT NULL DEFAULT 0 | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**CHECK:** `max_severity IN ('fatal','critical','major','minor','warning','normal')`

**Indexes:** `idx_products_tenant`, `idx_products_site`, `idx_products_max_severity`

---

### `product_ci`

M:N products ↔ configuration_items.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| product_id | UUID | PK, FK → products(id) ON DELETE CASCADE | |
| ci_id | UUID | PK, FK → configuration_items(id) ON DELETE CASCADE | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `idx_product_ci_ci_id`

---

### `raw_events`

Ingestion aggregate — events before/at dedup boundary.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| source_id | UUID | NOT NULL, FK → event_sources(id) | |
| external_id | VARCHAR(255) | | Idempotency key from source |
| title | VARCHAR(512) | NOT NULL | |
| description | TEXT | | |
| severity | VARCHAR(16) | NOT NULL | |
| status | VARCHAR(16) | NOT NULL DEFAULT 'new' | Snapshot at ingest |
| node_fqdn | VARCHAR(512) | | |
| ci_id | UUID | FK → configuration_items(id) | Resolved on ingest |
| payload | JSONB | NOT NULL DEFAULT '{}' | Normalized attributes |
| raw_payload | JSONB | NOT NULL DEFAULT '{}' | Original payload |
| source_at | TIMESTAMPTZ | NOT NULL | Event time at source |
| ingest_batch_id | UUID | | Batch correlation |
| processed | BOOLEAN | NOT NULL DEFAULT FALSE | |
| processed_event_id | UUID | FK → events(id) | Set after processing |
| processing_error | TEXT | | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Ingest time |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**CHECK:** `severity IN ('fatal','critical','major','minor','warning','normal')`

**Indexes:**
- `idx_raw_events_source_id_created_at` (source_id, created_at DESC)
- `idx_raw_events_processed`
- `idx_raw_events_ci_id`
- `idx_raw_events_severity`
- UNIQUE `uq_raw_events_source_external` (source_id, external_id) WHERE external_id IS NOT NULL

---

### `events`

Processing aggregate root — console events after rules/dedup.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| status | VARCHAR(16) | NOT NULL DEFAULT 'new' | |
| severity | VARCHAR(16) | NOT NULL | |
| title | VARCHAR(512) | NOT NULL | |
| description | TEXT | | |
| source_id | UUID | NOT NULL, FK → event_sources(id) | |
| ci_id | UUID | FK → configuration_items(id) | |
| node_fqdn | VARCHAR(512) | | Denormalized CI snapshot |
| system_name | VARCHAR(255) | | Denormalized |
| subsystem_name | VARCHAR(255) | | Denormalized |
| assigned_user_id | UUID | FK → users(id) ON DELETE SET NULL | |
| root_event_id | UUID | FK → events(id) ON DELETE SET NULL | Correlation parent |
| repeat_count | INTEGER | NOT NULL DEFAULT 1 | |
| tags | JSONB | NOT NULL DEFAULT '[]' | |
| attributes | JSONB | NOT NULL DEFAULT '{}' | Extended key/value attrs |
| itsm_incident_number | VARCHAR(64) | | MVP stub |
| raw_event_id | UUID | FK → raw_events(id) | Provenance |
| source_at | TIMESTAMPTZ | NOT NULL | Original source timestamp |
| last_repeat_at | TIMESTAMPTZ | | Last dedup increment |
| taken_at | TIMESTAMPTZ | | Operator take |
| closed_at | TIMESTAMPTZ | | |
| archived_at | TIMESTAMPTZ | | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | FM record creation |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**CHECK:** `status IN ('new','in_progress','closed','archived','maintenance','deferred')`  
**CHECK:** `severity IN ('fatal','critical','major','minor','warning','normal')`

**Indexes (console queries):**
- `idx_events_status` — filter by status
- `idx_events_severity` — filter by severity
- `idx_events_created_at` — sort / time range (DESC)
- `idx_events_source_id` — filter by source
- `idx_events_ci_id` — filter by CI / product drill-down
- `idx_events_assigned_user_id`
- `idx_events_root_event_id`
- `idx_events_status_severity_created` (status, severity, created_at DESC) — composite dashboard/console
- `idx_events_active` (severity, created_at DESC) WHERE status NOT IN ('closed','archived') — partial for active console

**Notes:** `child_event_ids` resolved via query `SELECT id FROM events WHERE root_event_id = ?` (not stored as array in MVP).

---

### `event_action_logs`

Domain audit journal (operator actions). MVP in PostgreSQL; prod replica to ClickHouse.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| event_id | UUID | NOT NULL, FK → events(id) ON DELETE CASCADE | |
| action | VARCHAR(64) | NOT NULL | take, close, comment, defer, maintenance, dedup, correlate, … |
| user_id | UUID | FK → users(id) ON DELETE SET NULL | NULL = system |
| user_name | VARCHAR(255) | NOT NULL | Denormalized snapshot |
| details | TEXT | NOT NULL | Human-readable description |
| metadata | JSONB | NOT NULL DEFAULT '{}' | Structured payload |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | Action timestamp |

**Indexes:** `idx_event_action_logs_event_id_created` (event_id, created_at DESC)

---

### `processing_rules`

Rules aggregate root.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| name | VARCHAR(255) | NOT NULL | |
| rule_type | VARCHAR(32) | NOT NULL | dedup, problem_resolution, correlation, threshold |
| enabled | BOOLEAN | NOT NULL DEFAULT FALSE | |
| trigger_type | VARCHAR(128) | NOT NULL | Событие потока / Событие FM |
| approval_status | VARCHAR(16) | NOT NULL DEFAULT 'draft' | approved, pending, draft |
| description | TEXT | | |
| canvas | JSONB | NOT NULL DEFAULT '{"nodes":[],"edges":[]}' | Visual rule graph |
| current_version_id | UUID | | FK → rule_versions(id), set after insert |
| last_run_at | TIMESTAMPTZ | | |
| created_by_user_id | UUID | FK → users(id) | |
| approved_by_user_id | UUID | FK → users(id) | |
| approved_at | TIMESTAMPTZ | | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**CHECK:** `rule_type IN ('dedup','problem_resolution','correlation','threshold')`  
**CHECK:** `approval_status IN ('approved','pending','draft')`

**Indexes:** `idx_processing_rules_enabled`, `idx_processing_rules_rule_type`, `idx_processing_rules_approval_status`

---

### `rule_versions`

Immutable snapshots on each save/approve.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| rule_id | UUID | NOT NULL, FK → processing_rules(id) ON DELETE CASCADE | |
| version_number | INTEGER | NOT NULL | Monotonic per rule |
| canvas | JSONB | NOT NULL | Snapshot of canvas at version |
| comment | TEXT | | Change note |
| created_by_user_id | UUID | FK → users(id) | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**UNIQUE:** (rule_id, version_number)  
**Indexes:** `idx_rule_versions_rule_id_version` (rule_id, version_number DESC)

---

### `event_maps`

Console saved filters (system + personal).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| name | VARCHAR(255) | NOT NULL | |
| query | TEXT | NOT NULL | Query DSL string |
| columns | JSONB | NOT NULL DEFAULT '[]' | Column keys array |
| is_system | BOOLEAN | NOT NULL DEFAULT FALSE | Built-in maps (Активные, Закрытые) |
| is_personal | BOOLEAN | NOT NULL DEFAULT TRUE | |
| owner_user_id | UUID | FK → users(id) ON DELETE CASCADE | NULL for system maps |
| sort_order | INTEGER | NOT NULL DEFAULT 0 | Sidebar ordering |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `idx_event_maps_owner_user_id`, `idx_event_maps_is_system`

**Seed data:** system maps `Активные` (`status != closed`), `Закрытые` (`status = closed`).

---

### `module_settings`

Singleton module configuration (one row, id fixed or `key = 'default'`).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| settings_key | VARCHAR(32) | NOT NULL UNIQUE DEFAULT 'default' | |
| timezone | VARCHAR(64) | NOT NULL DEFAULT 'Europe/Moscow' | |
| polling_interval_sec | INTEGER | NOT NULL DEFAULT 60 | |
| auto_archive_days | INTEGER | NOT NULL DEFAULT 30 | |
| repeat_interval_min | INTEGER | NOT NULL DEFAULT 15 | |
| wisla_integration | BOOLEAN | NOT NULL DEFAULT FALSE | |
| itsm_integration | BOOLEAN | NOT NULL DEFAULT FALSE | |
| notification_config | JSONB | NOT NULL DEFAULT '{}' | post-MVP |
| integration_config | JSONB | NOT NULL DEFAULT '{}' | post-MVP WISLA/ITSM |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

---

### `ci_health_snapshots`

Optional read-model for CI health drill-down (`/health/ci/:ciId`). Rebuilt on `EventUpdated` / scheduled job.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK DEFAULT gen_random_uuid() | |
| ci_id | UUID | NOT NULL UNIQUE, FK → configuration_items(id) ON DELETE CASCADE | |
| current_health | VARCHAR(16) | NOT NULL DEFAULT 'ok' | fatal…unknown |
| health_percent | NUMERIC(5,2) | NOT NULL DEFAULT 100 | |
| min_today_health | VARCHAR(16) | | |
| min_today_percent | NUMERIC(5,2) | | |
| max_today_health | VARCHAR(16) | | |
| max_today_percent | NUMERIC(5,2) | | |
| components | JSONB | NOT NULL DEFAULT '[]' | CiComponentHealth[] |
| dependents | JSONB | NOT NULL DEFAULT '[]' | DependentCi[] |
| signals_by_severity | JSONB | NOT NULL DEFAULT '{}' | Severity counters |
| timeline | JSONB | NOT NULL DEFAULT '[]' | 24h timeline points |
| calculated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**CHECK:** `current_health IN ('fatal','critical','major','warning','ok','unknown')`

**Indexes:** `idx_ci_health_snapshots_current_health`

**Note:** Product-level aggregates (`max_severity`, `active_event_count`) live on `products` table; CI health can be computed on-the-fly from `events` in MVP if snapshots are not populated.

---

## Post-MVP tables (not in initial migration)

Documented for architecture continuity; implement when downtime/consoles ship.

### `downtime_windows` (post-MVP)

| Column | Type | Description |
|--------|------|-------------|
| id | UUID PK | |
| name | VARCHAR(255) | |
| scope_type | VARCHAR(16) | ci, product, source |
| scope_id | UUID | Logical ref |
| schedule_type | VARCHAR(16) | permanent, temporary |
| starts_at / ends_at | TIMESTAMPTZ | |
| status | VARCHAR(16) | scheduled, active, completed, cancelled |
| suppressed_actions | JSONB | |
| created_at / updated_at | TIMESTAMPTZ | |

### `console_scopes` (post-MVP)

| Column | Type | Description |
|--------|------|-------------|
| id | UUID PK | |
| name | VARCHAR(255) | |
| tenant | VARCHAR(128) | |
| role_ids | JSONB | |
| created_at / updated_at | TIMESTAMPTZ | |

---

## DDL bootstrap (Liquibase `001_init.sql` outline)

```sql
-- Enums via CHECK constraints (excerpt)

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login           VARCHAR(128) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    team            VARCHAR(128),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    external_id     VARCHAR(255) UNIQUE,
    preferences     JSONB NOT NULL DEFAULT '{}',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT,
    permissions     JSONB NOT NULL DEFAULT '[]',
    system_role     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE event_sources (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    protocol        VARCHAR(64) NOT NULL,
    endpoint        VARCHAR(512) NOT NULL,
    api_key_hash    VARCHAR(255) NOT NULL,
    api_key_prefix  VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'inactive',
    schedule        VARCHAR(64),
    filter_rules    JSONB NOT NULL DEFAULT '{}',
    parser_config   JSONB NOT NULL DEFAULT '{}',
    adapter_version VARCHAR(32),
    last_success_at TIMESTAMPTZ,
    webhook_path_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_sources_type CHECK (type IN ('push_rest','push_snmp_trap','pull_etl')),
    CONSTRAINT chk_event_sources_status CHECK (status IN ('active','inactive','blocked'))
);

CREATE TABLE configuration_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fqdn            VARCHAR(512) NOT NULL UNIQUE,
    ci_type         VARCHAR(64) NOT NULL,
    system_name     VARCHAR(255) NOT NULL,
    subsystem_name  VARCHAR(255),
    software        VARCHAR(255),
    tags            JSONB NOT NULL DEFAULT '[]',
    external_ids    JSONB NOT NULL DEFAULT '{}',
    auto_created    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(64) UNIQUE,
    name                VARCHAR(255) NOT NULL,
    tenant              VARCHAR(128) NOT NULL,
    site                VARCHAR(128) NOT NULL,
    tags                JSONB NOT NULL DEFAULT '[]',
    max_severity        VARCHAR(16) NOT NULL DEFAULT 'normal',
    active_event_count  INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_products_max_severity CHECK (max_severity IN ('fatal','critical','major','minor','warning','normal'))
);

CREATE TABLE product_ci (
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    ci_id           UUID NOT NULL REFERENCES configuration_items(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_id, ci_id)
);

CREATE TABLE events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status              VARCHAR(16) NOT NULL DEFAULT 'new',
    severity            VARCHAR(16) NOT NULL,
    title               VARCHAR(512) NOT NULL,
    description         TEXT,
    source_id           UUID NOT NULL REFERENCES event_sources(id),
    ci_id               UUID REFERENCES configuration_items(id),
    node_fqdn           VARCHAR(512),
    system_name         VARCHAR(255),
    subsystem_name      VARCHAR(255),
    assigned_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    root_event_id       UUID REFERENCES events(id) ON DELETE SET NULL,
    repeat_count        INTEGER NOT NULL DEFAULT 1,
    tags                JSONB NOT NULL DEFAULT '[]',
    attributes          JSONB NOT NULL DEFAULT '{}',
    itsm_incident_number VARCHAR(64),
    raw_event_id        UUID,
    source_at           TIMESTAMPTZ NOT NULL,
    last_repeat_at      TIMESTAMPTZ,
    taken_at            TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_events_status CHECK (status IN ('new','in_progress','closed','archived','maintenance','deferred')),
    CONSTRAINT chk_events_severity CHECK (severity IN ('fatal','critical','major','minor','warning','normal'))
);

CREATE TABLE raw_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id           UUID NOT NULL REFERENCES event_sources(id),
    external_id         VARCHAR(255),
    title               VARCHAR(512) NOT NULL,
    description         TEXT,
    severity            VARCHAR(16) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'new',
    node_fqdn           VARCHAR(512),
    ci_id               UUID REFERENCES configuration_items(id),
    payload             JSONB NOT NULL DEFAULT '{}',
    raw_payload         JSONB NOT NULL DEFAULT '{}',
    source_at           TIMESTAMPTZ NOT NULL,
    ingest_batch_id     UUID,
    processed           BOOLEAN NOT NULL DEFAULT FALSE,
    processed_event_id  UUID REFERENCES events(id),
    processing_error    TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_raw_events_severity CHECK (severity IN ('fatal','critical','major','minor','warning','normal'))
);

ALTER TABLE events ADD CONSTRAINT fk_events_raw_event
    FOREIGN KEY (raw_event_id) REFERENCES raw_events(id);

CREATE TABLE event_action_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    action      VARCHAR(64) NOT NULL,
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    user_name   VARCHAR(255) NOT NULL,
    details     TEXT NOT NULL,
    metadata    JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE processing_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    rule_type           VARCHAR(32) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    trigger_type        VARCHAR(128) NOT NULL,
    approval_status     VARCHAR(16) NOT NULL DEFAULT 'draft',
    description         TEXT,
    canvas              JSONB NOT NULL DEFAULT '{"nodes":[],"edges":[]}',
    current_version_id  UUID,
    last_run_at         TIMESTAMPTZ,
    created_by_user_id  UUID REFERENCES users(id),
    approved_by_user_id UUID REFERENCES users(id),
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_processing_rules_type CHECK (rule_type IN ('dedup','problem_resolution','correlation','threshold')),
    CONSTRAINT chk_processing_rules_approval CHECK (approval_status IN ('approved','pending','draft'))
);

CREATE TABLE rule_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id             UUID NOT NULL REFERENCES processing_rules(id) ON DELETE CASCADE,
    version_number      INTEGER NOT NULL,
    canvas              JSONB NOT NULL,
    comment             TEXT,
    created_by_user_id  UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (rule_id, version_number)
);

ALTER TABLE processing_rules ADD CONSTRAINT fk_processing_rules_current_version
    FOREIGN KEY (current_version_id) REFERENCES rule_versions(id);

CREATE TABLE event_maps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    query           TEXT NOT NULL,
    columns         JSONB NOT NULL DEFAULT '[]',
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    is_personal     BOOLEAN NOT NULL DEFAULT TRUE,
    owner_user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE module_settings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settings_key            VARCHAR(32) NOT NULL UNIQUE DEFAULT 'default',
    timezone                VARCHAR(64) NOT NULL DEFAULT 'Europe/Moscow',
    polling_interval_sec    INTEGER NOT NULL DEFAULT 60,
    auto_archive_days       INTEGER NOT NULL DEFAULT 30,
    repeat_interval_min     INTEGER NOT NULL DEFAULT 15,
    wisla_integration       BOOLEAN NOT NULL DEFAULT FALSE,
    itsm_integration        BOOLEAN NOT NULL DEFAULT FALSE,
    notification_config     JSONB NOT NULL DEFAULT '{}',
    integration_config      JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ci_health_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ci_id               UUID NOT NULL UNIQUE REFERENCES configuration_items(id) ON DELETE CASCADE,
    current_health      VARCHAR(16) NOT NULL DEFAULT 'ok',
    health_percent      NUMERIC(5,2) NOT NULL DEFAULT 100,
    min_today_health    VARCHAR(16),
    min_today_percent   NUMERIC(5,2),
    max_today_health    VARCHAR(16),
    max_today_percent   NUMERIC(5,2),
    components          JSONB NOT NULL DEFAULT '[]',
    dependents          JSONB NOT NULL DEFAULT '[]',
    signals_by_severity JSONB NOT NULL DEFAULT '{}',
    timeline            JSONB NOT NULL DEFAULT '[]',
    calculated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ci_health_level CHECK (current_health IN ('fatal','critical','major','warning','ok','unknown'))
);

-- Performance indexes
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_severity ON events(severity);
CREATE INDEX idx_events_created_at ON events(created_at DESC);
CREATE INDEX idx_events_source_id ON events(source_id);
CREATE INDEX idx_events_ci_id ON events(ci_id);
CREATE INDEX idx_events_status_severity_created ON events(status, severity, created_at DESC);
CREATE INDEX idx_events_active ON events(severity, created_at DESC)
    WHERE status NOT IN ('closed', 'archived');

CREATE INDEX idx_raw_events_source_id_created_at ON raw_events(source_id, created_at DESC);
CREATE INDEX idx_raw_events_processed ON raw_events(processed);
CREATE INDEX idx_raw_events_ci_id ON raw_events(ci_id);
CREATE UNIQUE INDEX uq_raw_events_source_external ON raw_events(source_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX idx_event_action_logs_event_id_created ON event_action_logs(event_id, created_at DESC);
CREATE INDEX idx_product_ci_ci_id ON product_ci(ci_id);
CREATE INDEX idx_configuration_items_tags ON configuration_items USING GIN (tags);
```

---

## Migrations plan (Liquibase)

| ChangeSet | Description |
|-----------|-------------|
| `001_extensions.sql` | `pgcrypto` |
| `002_identity.sql` | users, roles, user_roles |
| `003_configuration.sql` | event_sources |
| `004_cmdb.sql` | configuration_items, products, product_ci |
| `005_ingestion.sql` | raw_events |
| `006_processing.sql` | events, event_action_logs, FK raw_event_id |
| `007_rules.sql` | processing_rules, rule_versions, FK current_version |
| `008_console.sql` | event_maps + seed system maps |
| `009_settings.sql` | module_settings + default row |
| `010_health.sql` | ci_health_snapshots |
| `011_indexes.sql` | Console/query indexes (if not in table scripts) |
| `012_seed_dev.sql` | Dev seed (optional, not prod) |

---

## API ↔ DB mapping (selected)

| API field (camelCase) | DB column |
|-----------------------|-----------|
| `fullName` | users.full_name |
| `roleIds` | user_roles.role_id (join) |
| `nodeFqdn` | events.node_fqdn |
| `systemName` | events.system_name |
| `repeatCount` | events.repeat_count |
| `assignedUserId` | events.assigned_user_id |
| `rootEventId` | events.root_event_id |
| `childEventIds` | query children by root_event_id |
| `sourceAt` | events.source_at |
| `lastRepeatAt` | events.last_repeat_at |
| `takenAt` | events.taken_at |
| `closedAt` | events.closed_at |
| `itsmIncidentNumber` | events.itsm_incident_number |
| `ciType` | configuration_items.ci_type |
| `maxSeverity` | products.max_severity |
| `activeEventCount` | products.active_event_count |
| `approvalStatus` | processing_rules.approval_status |
| `ruleType` | processing_rules.rule_type |
| `lastSuccessAt` | event_sources.last_success_at |
| `apiKey` | event_sources.api_key_prefix (masked); hash internal |
| `pollingIntervalSec` | module_settings.polling_interval_sec |

---

## Cross-service references

| Field | Target | Notes |
|-------|--------|-------|
| `event_sources.id` | adapter `source_config_snapshots.source_id` | Logical UUID, polled via `GET /api/v1/internal/sources/{id}/config` |
| `events.id` | adapter buffered message ack | No FK |
| `configuration_items.external_ids` | WISLA CMDB | post-MVP integration |

---

## Transaction boundaries

| Operation | Tables (single TX) |
|-----------|-------------------|
| Operator action | `events` UPDATE + `event_action_logs` INSERT |
| Ingest batch | `raw_events` INSERT (+ optional `configuration_items` UPSERT) |
| Event processing | `raw_events` UPDATE + `events` INSERT/UPDATE + `event_action_logs` |
| Rule save | `processing_rules` UPDATE + `rule_versions` INSERT + version pointer |
| Product health refresh | `products` UPDATE (+ optional `ci_health_snapshots` UPSERT) |

---

*Schema aligned with `docs/architecture.md`, `docs/fm-module/api.yaml`, and `prototype/src/data/mock.ts`.*

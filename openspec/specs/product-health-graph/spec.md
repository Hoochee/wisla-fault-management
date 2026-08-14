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

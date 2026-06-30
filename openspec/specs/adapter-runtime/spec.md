# adapter-runtime

## Purpose

Runtime visibility of the adapter service and per-source adapter status for the admin UI.

## Requirements

### Requirement: Runtime adapter visibility

The system SHALL expose adapter service runtime state to authenticated admin users via `GET /api/v1/adapters/runtime`.

#### Scenario: Adapter healthy

- **WHEN** adapter `/health` returns status ok
- **THEN** response includes service status, version, database, fm-module reachability, buffered message count

#### Scenario: Adapter down

- **WHEN** adapter health call fails or times out
- **THEN** service status is `down` and source rows show `unreachable` for active sources

### Requirement: Per-source adapter runtime status

The system SHALL derive `adapterRuntimeStatus` for each configured source.

#### Scenario: Active source with recent traffic

- **WHEN** source `status` is `active` and `lastSuccessAt` is within threshold
- **THEN** `adapterRuntimeStatus` is `running`

#### Scenario: Inactive source

- **WHEN** source `status` is `inactive`
- **THEN** `adapterRuntimeStatus` is `stopped`

### Requirement: Sources page UI

The `/sources` page SHALL display adapter service card and per-source runtime column with filters (Все / Запущен / Остановлен).

#### Scenario: Filter running

- **WHEN** user selects filter «Запущен»
- **THEN** only sources with `adapterRuntimeStatus` in (`running`, `idle`) are shown

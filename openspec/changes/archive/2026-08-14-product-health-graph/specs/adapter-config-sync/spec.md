## ADDED Requirements

### Requirement: Internal source index includes type, schedule, and parserConfig

`GET /api/v1/internal/sources` (header `X-Service-Key`) SHALL include `type`, `schedule`, and `parserConfig` in addition to the existing `sourceId`, `sourceKey`, `apiKeyHash`, `status`, and `filterRules`. Existing webhook sync behaviour SHALL remain.

#### Scenario: pull_etl fields are returned

- **GIVEN** an event source with `type=pull_etl`, `schedule=30s`, and a Prometheus `parserConfig`
- **WHEN** the adapter calls `GET /api/v1/internal/sources` with a valid `X-Service-Key`
- **THEN** each matching item includes `type`, `schedule`, and `parserConfig`
- **AND** `sourceId`, `sourceKey`, `apiKeyHash`, `status`, and `filterRules` are still present

#### Scenario: push_rest still syncs

- **GIVEN** a `push_rest` source
- **WHEN** the same endpoint is called
- **THEN** `type` is `push_rest`
- **AND** `parserConfig` may be empty object
- **AND** webhook ingest continues to work

### Requirement: Adapter snapshot stores pull_etl sync fields

On config sync the adapter SHALL persist `source_type`, `schedule`, and `parser_config` on `source_config_snapshots` and expose them on the domain `SourceConfig` used by pull scraping. Sync SHALL run after source create, status change, API key regeneration, and when `type`, `schedule`, or `parserConfig` change.

#### Scenario: Snapshot columns populated

- **GIVEN** fm-module returns a `pull_etl` source with schedule and parserConfig
- **WHEN** `SyncSourceConfigUseCase` runs
- **THEN** `source_config_snapshots` stores `source_type=pull_etl`, the schedule, and the parserConfig JSON
- **AND** `SourceConfig` used by scrape contains those fields

#### Scenario: ParserConfig change is picked up

- **GIVEN** an already synced `pull_etl` source
- **WHEN** an administrator updates `parserConfig.rules` and sync runs
- **THEN** the snapshot `parser_config` matches the new rules
- **AND** the next scrape uses the updated thresholds

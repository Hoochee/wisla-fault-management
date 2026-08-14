# adapter-pull-metrics Specification

## Purpose

HTTP scrape of Prometheus `/metrics` for `pull_etl` sources: threshold evaluation, PROBLEM/OK only on state change, stable `externalId`, persist `pull_metric_states`, publish via existing Kafka ingest.

## Requirements

### Requirement: Adapter scrapes Prometheus metrics for pull_etl sources

For each active source with `type` `pull_etl`, the adapter SHALL HTTP GET each URL in `parserConfig.targets` on the source `schedule` and parse Prometheus text exposition. Sources with `type` `push_rest` or `push_snmp_trap` MUST NOT be scraped. The adapter MUST NOT invoke scripts, SSH, or container exec.

#### Scenario: Scheduled scrape of pull_etl targets

- **GIVEN** an active `pull_etl` source with schedule `30s` and a target `http://giftshop-catalog:8092/metrics`
- **WHEN** `PullEtlScheduler` fires
- **THEN** the adapter performs HTTP GET on that URL
- **AND** parsed metric samples are evaluated against `parserConfig.rules`

#### Scenario: Push sources are ignored by the pull scheduler

- **GIVEN** an active `push_rest` source
- **WHEN** `PullEtlScheduler` fires
- **THEN** the adapter does not HTTP GET that source

#### Scenario: No script collector

- **GIVEN** the adapter pull implementation
- **WHEN** its outbound ports are inspected
- **THEN** there is no port or adapter that executes OS scripts, SSH, or `docker exec`

### Requirement: Thresholds emit PROBLEM and OK events only on state change

The adapter SHALL compare scraped values to `parserConfig.rules` thresholds (`warning`, `major`, `critical`) and emit a raw event only when the evaluated severity (including OK) differs from `pull_metric_states.last_severity`. Repeated scrapes with the same severity MUST NOT publish.

#### Scenario: Crossing a threshold publishes PROBLEM

- **GIVEN** metric `process_cpu_usage` last state OK and rule thresholds warning 0.70, major 0.85, critical 0.95
- **WHEN** a scrape reads 0.90
- **THEN** the adapter publishes one event with severity `major` and problem status
- **AND** `pull_metric_states` stores `last_severity=major`

#### Scenario: Recovery publishes OK once

- **GIVEN** last severity `major` for that metric
- **WHEN** a scrape reads 0.10 (below warning)
- **THEN** the adapter publishes one event with `status=ok`
- **AND** does not publish again while the value stays below warning

#### Scenario: Unchanged state is silent

- **GIVEN** last severity `critical` and a new scrape still in the critical band
- **WHEN** the evaluator runs
- **THEN** no Kafka message is published

### Requirement: Pull events use a stable externalId and existing Kafka ingest

Each emitted event SHALL use `externalId` `{sourceKey}:{ciFqdn}:{metricName}` and SHALL be published through the existing `RawEventPublisherPort` to topic `fm.raw-events` with the current `RawEventEnvelope`. The adapter MUST NOT call `POST /api/v1/ingest` for pull results.

#### Scenario: Stable externalId

- **GIVEN** sourceKey `giftshop-metrics`, ciFqdn `giftshop-catalog.demo`, metric `process_cpu_usage`
- **WHEN** a PROBLEM event is emitted
- **THEN** `externalId` is `giftshop-metrics:giftshop-catalog.demo:process_cpu_usage`

#### Scenario: Kafka path only

- **GIVEN** a state-changing scrape result
- **WHEN** the use case publishes
- **THEN** `RawEventPublisherPort` is invoked
- **AND** no HTTP ingest client is used

### Requirement: Pull metric state is stored in adapter database

The adapter SHALL persist scrape state in table `pull_metric_states` with primary key `(source_id, external_id)` and columns `last_severity`, `last_value`, `updated_at`.

#### Scenario: State row upsert

- **GIVEN** no row for `(source_id, external_id)`
- **WHEN** the first PROBLEM is emitted
- **THEN** a `pull_metric_states` row is inserted with the evaluated severity and value

### Requirement: Metric threshold evaluator is a Spring-free domain service

Threshold comparison SHALL live in domain code tested without Spring (`MetricThresholdEvaluatorTest`). Invert rules (e.g. `up == 0` is critical) MUST be supported when `parserConfig.rules[].invert` is true.

#### Scenario: Invert up metric

- **GIVEN** rule `{ "metric": "up", "thresholds": { "critical": 0 }, "invert": true }`
- **WHEN** the scraped `up` value is 0
- **THEN** evaluated severity is `critical`

#### Scenario: Evaluator unit test has no Spring

- **GIVEN** `MetricThresholdEvaluatorTest`
- **WHEN** it runs under Surefire
- **THEN** it constructs the evaluator with plain Java
- **AND** covers warning, major, critical, OK, and invert bands

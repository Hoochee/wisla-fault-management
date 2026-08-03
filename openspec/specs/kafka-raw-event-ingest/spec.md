# kafka-raw-event-ingest

## Purpose

Kafka-based raw event ingest path from adapter to fm-module: publish after prefilter, consume into IngestService semantics, with buffer/retry and local compose wiring.

## Requirements

### Requirement: Adapter publishes raw ingest to Kafka after prefilter

After a webhook passes API-key validation and prefilter rules, the adapter SHALL publish a JSON message to Kafka topic `fm.raw-events` (configurable name allowed) instead of calling fm-module HTTP ingest as the default outbound path.

#### Scenario: Successful publish after prefilter

- **WHEN** a valid webhook for an active source passes prefilter and Kafka accepts the produce
- **THEN** the adapter responds with accepted delivery indicating the event was published (forwarded/published) and does not call `POST /api/v1/ingest`

#### Scenario: Filtered event is not published

- **WHEN** prefilter rules drop the webhook payload
- **THEN** the adapter rejects the request and does not publish to `fm.raw-events`

#### Scenario: Dual-path is not default

- **WHEN** adapter runs with default configuration
- **THEN** a successful webhook ingest path publishes to Kafka only and does not also HTTP-post the same batch to fm-module

### Requirement: Kafka message envelope aligns with IngestRequest and source identity

Each `fm.raw-events` message SHALL carry a versioned JSON envelope that includes non-secret source identity and a `body` matching the current `IngestRequest` shape produced by the adapter mapper (events batch and/or heartbeat).

#### Scenario: Event batch envelope

- **WHEN** the adapter publishes a normal (non-probe) ingest after mapping
- **THEN** the message includes `schemaVersion`, `messageId`, `producedAt`, `sourceId`, webhook `sourceKey`, and `body` with `events`, `adapterVersion`, and `receivedAt` fields compatible with `IngestRequest`

#### Scenario: Heartbeat envelope

- **WHEN** the adapter publishes a heartbeat/probe-mapped payload
- **THEN** the message `body` has `heartbeat` true and does not require event rows for successful consumption semantics

#### Scenario: API secret not on the bus

- **WHEN** a message is published to `fm.raw-events`
- **THEN** the envelope does not contain the source API key/secret used for webhook authentication

### Requirement: Buffer and retry on Kafka unavailability

When Kafka publish fails with a retryable error, the adapter SHALL persist the payload in the existing local buffer and retry publish later with the same backoff behavior family as today's buffer retry worker.

#### Scenario: Broker unavailable buffers message

- **WHEN** webhook prefilter succeeds but Kafka publish fails retryably (broker down/timeout)
- **THEN** the adapter stores a buffered message and returns accepted delivery indicating buffered

#### Scenario: Retry worker publishes to Kafka

- **WHEN** a buffered message is due for retry and Kafka is available
- **THEN** the retry worker publishes to `fm.raw-events` and deletes the buffer row on success

#### Scenario: Retry worker does not use HTTP ingest by default

- **WHEN** buffer retry runs under default configuration
- **THEN** it does not call fm-module `POST /api/v1/ingest` to drain the buffer

### Requirement: fm-module consumes fm.raw-events into IngestService semantics

fm-module SHALL consume `fm.raw-events` with consumer group `fm-module-ingestion` (configurable) and apply the same ingest outcomes as HTTP `IngestService` for the resolved source: persist raw events and process batch, or apply heartbeat metadata updates without raw events.

#### Scenario: Event message creates raw events

- **WHEN** a valid event-batch envelope for an existing `sourceId` is consumed
- **THEN** fm-module persists raw events for that source, updates source success metadata, and triggers batch processing equivalent to HTTP ingest

#### Scenario: Heartbeat message updates source only

- **WHEN** a valid heartbeat envelope for an existing `sourceId` is consumed
- **THEN** fm-module updates source `lastSuccessAt` / adapter version as applicable and does not create raw events

#### Scenario: Unknown source is not ingested

- **WHEN** a message references a `sourceId` that does not exist
- **THEN** fm-module does not create raw events for that message and handles it as a permanent consume error (skip after log) without failing the whole consumer indefinitely

### Requirement: At-least-once consumption with offset commit after success

The fm-module consumer SHALL provide at-least-once processing: commit the Kafka offset only after successful ingest handling for that record; transient failures SHALL cause redelivery.

#### Scenario: Commit after successful ingest

- **WHEN** `IngestService` handling completes successfully for a consumed record
- **THEN** the consumer commits the record offset

#### Scenario: Transient failure redelivers

- **WHEN** ingest handling fails due to a transient error (for example database unavailable)
- **THEN** the offset is not committed and the message can be redelivered

### Requirement: HTTP ingest endpoint retained for tests and debug

fm-module SHALL keep `POST /api/v1/ingest` with existing source API-key authentication for manual tests and debug; adapter default production outbound path SHALL NOT depend on this endpoint.

#### Scenario: HTTP ingest still works

- **WHEN** a client calls `POST /api/v1/ingest` with a valid source API key and body
- **THEN** fm-module processes the request via existing ingest semantics

#### Scenario: Adapter default path is Kafka

- **WHEN** adapter default configuration delivers a prefiltered webhook
- **THEN** delivery uses Kafka publish and does not require fm-module HTTP ingest to succeed

### Requirement: Local compose provides Kafka for adapter and fm-module

`backend/docker-compose.yaml` SHALL include a Kafka broker reachable by adapter and fm-module services with configuration so both can use topic `fm.raw-events` in local/demo runs.

#### Scenario: Compose stack wires bootstrap servers

- **WHEN** the demo compose stack is started
- **THEN** Kafka is running and adapter/fm-module are configured with bootstrap servers pointing at that broker for raw-event ingest
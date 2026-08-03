## Why

Adapter today forwards prefiltered webhook payloads to fm-module over synchronous HTTPS (`POST /api/v1/ingest`). That couples availability and latency of the two deployables and blocks the production topology already documented in `docs/architecture.md` (topic `fm.raw-events`). Kafka libraries and compose wiring are not present yet; this change introduces the first production ingest path over Kafka while keeping source→adapter webhooks on HTTP.

## What Changes

- **BREAKING (adapter outbound ingest):** adapter stops using HTTP as the default path to deliver ingest batches to fm-module; after webhook prefilter it publishes to Kafka topic `fm.raw-events` only (no dual-path HTTP+Kafka by default).
- adapter local buffer/retry continues, but retryable failure means Kafka publish/broker unavailability (not fm-module HTTP 5xx/timeout).
- fm-module adds a Kafka consumer for `fm.raw-events` that applies the same `IngestService` semantics as today’s HTTP ingest (persist `RawEvent`, update source `lastSuccessAt` / adapter version, process batch; heartbeat updates source metadata without raw events).
- fm-module keeps `POST /api/v1/ingest` for tests, debug, and any non-adapter callers; it is no longer the adapter’s production outbound path.
- `backend/docker-compose.yaml` gains a Kafka broker (and required deps) with topic wiring for local/demo runs.
- Source→adapter webhook HTTP contract is unchanged; config sync, probe redesign, health-over-Kafka, frontend, Liquibase (unless proven required), `fm.config-events`, and `fm.domain-events` stay out of scope.

## Capabilities

### New Capabilities
- `kafka-raw-event-ingest`: End-to-end raw-event ingest over Kafka topic `fm.raw-events` — adapter producer after prefilter, message envelope aligned with `IngestRequest` + source identity, fm-module consumer group invoking `IngestService`, buffer/retry on Kafka unavailability, compose Kafka for local stack.

### Modified Capabilities
- _(none)_ — existing `adapter-runtime` / `adapter-config-sync` requirement text stays HTTP/config oriented; this change does not alter those spec-level behaviors.

## Impact

- **Modules:** `backend/adapter`, `backend/fm-module`, `backend/docker-compose.yaml` (Kafka service + env for both apps).
- **APIs:** adapter webhook HTTP unchanged; adapter→fm-module ingest HTTP removed from default production path (**BREAKING** for that hop); fm-module `POST /api/v1/ingest` retained for tests/debug.
- **Dependencies:** Spring Kafka (producer in adapter, consumer in fm-module); Kafka broker in compose; test support via `spring-kafka-test` (`@EmbeddedKafka`) — repo has no Testcontainers pattern today.
- **Ops:** both services need `KAFKA_BOOTSTRAP_SERVERS` (or equivalent); adapter no longer requires fm-module HTTP reachability for successful ingest delivery (health check of fm-module may remain separate).
- **Out of scope:** frontend; config sync / probe / health over Kafka; topics `fm.config-events` / `fm.domain-events`; Liquibase unless a proven schema need appears during apply.

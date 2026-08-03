## Context

Today the ingest path is:

1. External source → adapter `POST /webhook/{sourceKey}` (HTTP)
2. Adapter validates API key, applies `filter_rules`, maps payload via `IngestPayloadMapper` to an `IngestRequest`-shaped body
3. `WebhookService.deliver` / `BufferRetryWorker` call `FmModuleClient.forwardIngest` → `POST {FM_MODULE_BASE_URL}/api/v1/ingest?sourceKey=…`
4. fm-module `IngestController` authenticates via source API key (`SourceApiKeyAuthenticationFilter`) and delegates to `IngestService`

`docs/architecture.md` already names production topic `fm.raw-events` (adapter → fm-module ingestion). Code has no Kafka dependencies, no broker in compose, and no consumer. Approved scope replaces only this outbound ingest hop with Kafka; webhook ingress, config sync, and health remain HTTP.

Stakeholders: backend adapter + fm-module; local/demo via docker-compose. Frontend unchanged.

## Goals / Non-Goals

**Goals:**

- Decouple adapter ingest delivery from fm-module HTTP availability using topic `fm.raw-events`.
- Preserve webhook prefilter → map → deliver semantics; only the transport behind `deliver` / buffer retry changes.
- Consume messages into the same `IngestService` behavior (events + heartbeat) as HTTP ingest.
- Wire Kafka in compose for local/demo; document env/config for both services.
- Keep `POST /api/v1/ingest` on fm-module for tests/debug.
- Adapt buffer/retry to Kafka publish failures (at-least-once produce with local DB buffer).
- Default cutover: Kafka-only outbound ingest (no dual-path HTTP+Kafka).

**Non-Goals:**

- Moving config sync, probe-specific protocols, or health checks onto Kafka.
- Topics `fm.config-events` / `fm.domain-events`.
- Frontend / UI changes.
- Liquibase schema work unless apply proves a hard requirement (expect none: buffer table already exists).
- Dual-write HTTP+Kafka as a supported default mode.
- Cross-DC / multi-cluster Kafka ops, Schema Registry, Avro.

## Decisions

### D1 — Topic and payload contract

- **Decision:** Use single topic `fm.raw-events`. Message value is JSON envelope:

```json
{
  "schemaVersion": 1,
  "messageId": "<uuid>",
  "producedAt": "<ISO-8601 instant>",
  "sourceId": "<uuid>",
  "sourceKey": "<webhook path key>",
  "body": {
    "heartbeat": false,
    "events": [ /* IngestRequest.IngestEventPayload */ ],
    "adapterVersion": "1.0.0",
    "receivedAt": "<ISO-8601 instant>"
  }
}
```

- `body` MUST match current `IngestRequest` field shapes (same mapper output as today).
- `sourceId` is the trusted identity for consumer resolution (adapter already has it on `SourceConfigSnapshot` after config sync). Do **not** put the source API secret in the Kafka value.
- `sourceKey` is the non-secret webhook path key (for logs / ops), not the API key.
- Kafka record key: `sourceId` (string) for partition affinity per source.
- **Alternatives:** raw `IngestRequest` only (loses source identity without HTTP auth) — rejected; put API key in headers like HTTP — rejected (secret on the bus).

### D2 — Producer placement (adapter)

- **Decision:** Introduce `RawEventKafkaPublisher` (name flexible) used by `WebhookService.deliver` and `BufferRetryWorker` instead of `FmModuleClient.forwardIngest` for ingest batches.
- On successful Kafka send (ack from broker per configured acks), treat as `delivery=forwarded` (or equivalent “published”).
- On retryable publish failure (broker down, timeout, retriable Kafka exception), buffer as today and return `delivery=buffered`.
- Non-retryable mapping/serialization errors: fail the webhook with 4xx/5xx without buffering (same spirit as non-retryable HTTP reject).
- `FmModuleClient` remains for **health reachability** and any non-ingest HTTP (config sync client paths stay as-is). Probe that currently shares `deliver` will publish via Kafka as a consequence of shared deliver — no probe-specific Kafka design.
- **Alternatives:** dual-path feature flag HTTP|Kafka — rejected as default by scope; keep HTTP ingest client behind flag only if urgently needed for rollback (optional kill-switch, not dual-write).

### D3 — Consumer (fm-module)

- **Decision:** Spring Kafka `@KafkaListener` (or `ConcurrentKafkaListenerContainerFactory`) on `fm.raw-events`.
- **Consumer group:** `fm-module-ingestion` (stable id; scale by adding fm-module instances sharing the group).
- Resolve `EventSourceEntity` by envelope `sourceId`; if missing/inactive, log and skip with commit after DLQ decision (see D5) — do not call `IngestService` with fabricated auth.
- Build a service-layer entry that reuses `IngestService` logic without HTTP `Authentication`: prefer extracting/resolving source and calling an overload such as `ingest(IngestRequest, UUID sourceId)` (or internal facade) so HTTP controller and Kafka consumer share one path. HTTP controller continues to resolve `sourceId` from API-key auth.
- Ack (commit offset) only after successful `IngestService` completion for that record (enable manual ack or default sync commit after listener returns without exception).
- **Alternatives:** separate processing pipeline bypassing `IngestService` — rejected (must keep same semantics).

### D4 — Delivery semantics & idempotency

- **Decision:** At-least-once end-to-end.
  - Producer: wait for broker ack (`acks=all` recommended for demo durability with single broker still acceptable for local).
  - Consumer: process then commit; on failure throw → redelivery.
- Idempotency: include `messageId` in envelope. MVP may not persist a dedicated idempotency table (no Liquibase unless proven needed); document that redelivery can create duplicate `RawEvent` rows for the same logical webhook if processing lacks stronger dedupe. Prefer relying on existing processing/correlation behavior where present; optional follow-up: upsert/skip by `(sourceId, messageId)` if duplicates prove painful.
- Heartbeat messages use the same envelope with `body.heartbeat=true` and empty/absent events.

### D5 — Poison messages

- **Decision:** After N consecutive consumer failures for the same record (or immediately for validation/schema errors), log at error with `messageId`/`sourceId` and commit skip **or** publish to a local dead-letter topic `fm.raw-events.dlq` if cheap to add in compose. Prefer: validation errors → skip+commit; transient DB errors → retry (no commit). Exact N and DLQ are implementer choice documented in code comments; default recommendation: no DLQ topic in MVP compose, skip+metric/log for permanent validation failures.

### D6 — Compose & configuration

- **Decision:** Add Kafka to `backend/docker-compose.yaml` (KRaft single-node image such as `bitnami/kafka` or `apache/kafka` — pick one maintained image; expose `9092` to host).
- Services `adapter` / `adapter-2` / `fm-module` get:
  - `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` (or `SPRING_KAFKA_BOOTSTRAP_SERVERS`)
  - topic name `wisla.kafka.raw-events-topic: fm.raw-events` (configurable)
  - consumer group for fm-module as above
- Adapter `depends_on` Kafka healthy (not only fm-module) for publish path; fm-module depends on Kafka + postgres.
- Auto-create topic: enable broker auto-create for local **or** init container/script — prefer explicit topic create in compose health/init for predictability (`fm.raw-events`, partitions ≥ 1; 3 partitions acceptable for demo).
- Application YAML: `spring.kafka.*` producer (adapter) / consumer (fm-module).

### D7 — Test strategy (TDD order)

Repo tests use `@SpringBootTest` + H2; **no Testcontainers** today.

- **Decision:** Use `spring-kafka-test` `@EmbeddedKafka` for producer/consumer integration tests (fits existing in-JVM style). Do not introduce Testcontainers unless apply phase hits a hard EmbeddedKafka limitation.
- **TDD order (high level):**
  1. Envelope serialization contract tests (adapter + shared shape).
  2. Adapter publisher unit/IT: success ack → forwarded; broker down → buffered.
  3. Buffer retry publishes to Kafka (not HTTP).
  4. fm-module consumer IT: message → `IngestService` side effects (raw event / heartbeat) with EmbeddedKafka.
  5. Existing webhook controller tests updated to stub/publisher instead of `FmModuleClient.forwardIngest` for ingest.
  6. Keep/extend `IngestControllerTest` for HTTP debug path (unchanged contract).
- Frontend tests: N/A.

### D8 — Cutover / rollback

- **Decision:** Single cutover in this change: remove adapter production dependency on HTTP ingest. Optional emergency property `wisla.adapter.ingest-transport=kafka|http` is **not** required by scope; if added for rollback, default MUST be `kafka` and must not dual-write.
- Rollback: revert deploy + restore previous adapter image that uses `FmModuleClient`; fm-module HTTP ingest never removed.

## Risks / Trade-offs

- **[Risk] Duplicate events on redelivery** → Mitigation: `messageId` in envelope; document at-least-once; optional later idempotency store; processing dedupe where already present.
- **[Risk] Consumer cannot auth via API key** → Mitigation: trusted `sourceId` on bus after adapter webhook auth; restrict Kafka network to internal compose/cluster.
- **[Risk] Adapter health still probes fm-module HTTP while ingest no longer needs it** → Mitigation: keep health semantics for UI runtime card; clarify in ops notes that ingest path health ≠ Kafka publish health (consider adding Kafka publish lag/metric later — out of scope).
- **[Risk] Probe/heartbeat now rides Kafka via shared deliver** → Mitigation: acceptable under “no probe-over-Kafka redesign”; HTTP ingest remains for manual probes if needed.
- **[Risk] Local compose complexity / broker startup time** → Mitigation: healthchecks + depends_on; document wait in README briefly during apply.
- **[Trade-off] EmbeddedKafka vs Testcontainers** → Prefer EmbeddedKafka for CI parity with H2-style tests; less production-fidelity broker behavior.

## Migration Plan

1. Land Kafka deps + config + compose broker (both modules build/boot with Kafka optional-fail-fast on missing bootstrap in prod profile).
2. Implement producer + switch deliver/retry; ship adapter after broker available.
3. Implement consumer + shared `IngestService` entry; ship fm-module before or with adapter (consumer idle until messages appear).
4. Verify demo path: simulator → webhook → Kafka → console events.
5. Confirm `POST /api/v1/ingest` still works for curl/tests.
6. Rollback: redeploy previous adapter (HTTP ingest); leave consumer idle or disable listener.

## Open Questions

- Exact Kafka Docker image/tag for compose (Bitnami vs Apache Kafka KRaft) — pick during apply; not blocking design.
- Whether to add optional `ingest-transport` kill-switch — default no unless ops requests during review.
- Whether permanent poison messages need a DLQ topic in compose for MVP — default skip+log unless apply finds silent drop unacceptable.

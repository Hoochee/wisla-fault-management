## 1. Compose & shared config

- [x] 1.1 Add Kafka broker service (KRaft) to `backend/docker-compose.yaml` with healthcheck and host port for local use
- [x] 1.2 Wire `KAFKA_BOOTSTRAP_SERVERS` (or Spring-equivalent) into `adapter`, `adapter-2`, and `fm-module`; ensure topic `fm.raw-events` exists (auto-create or init)
- [x] 1.3 Document new env vars briefly in adapter and fm-module README ingest sections (Kafka bootstrap, topic name, consumer group)

## 2. Contract & dependencies (TDD-first)

- [x] 2.1 Add `spring-kafka` (+ `spring-kafka-test` for tests) to `backend/adapter/pom.xml` and `backend/fm-module/pom.xml`
- [x] 2.2 Define shared envelope JSON shape (`schemaVersion`, `messageId`, `producedAt`, `sourceId`, `sourceKey`, `body` = `IngestRequest` fields) in code + unit tests for serialize/deserialize round-trip
- [x] 2.3 Add application config properties for bootstrap servers, topic `fm.raw-events`, producer acks, and fm-module consumer group `fm-module-ingestion`

## 3. Adapter producer (TDD)

- [x] 3.1 RED: tests for publisher — broker ack ⇒ success; retryable failure ⇒ signaled retryable; serialization/permanent failure ⇒ non-retryable
- [x] 3.2 GREEN: implement Kafka publisher used by ingest delivery
- [x] 3.3 RED/GREEN: update `WebhookService.deliver` to publish via Kafka (not `FmModuleClient.forwardIngest`); adjust webhook/controller tests to stub publisher
- [x] 3.4 RED/GREEN: update `BufferRetryWorker` to republish via Kafka; assert default path does not HTTP-ingest
- [x] 3.5 Ensure buffer-on-Kafka-down behavior preserves accepted `buffered` webhook response semantics

## 4. fm-module consumer (TDD)

- [x] 4.1 Refactor `IngestService` (or facade) so HTTP controller and Kafka consumer share source-id-based ingest without duplicating persist/process logic — cover with unit tests
- [x] 4.2 RED: `@EmbeddedKafka` consumer test — event envelope ⇒ raw events + processing side effects for known `sourceId`
- [x] 4.3 RED/GREEN: heartbeat envelope updates source metadata without raw events
- [x] 4.4 RED/GREEN: unknown `sourceId` does not create raw events; consumer recovers (skip/log)
- [x] 4.5 Implement listener with commit-after-success / redelivery on transient failure; verify with tests where practical
- [x] 4.6 Keep `POST /api/v1/ingest` and existing `IngestControllerTest` (HTTP debug path) green

## 5. Cutover verification

- [x] 5.1 Confirm adapter default config has no dual-write HTTP+Kafka
- [x] 5.2 Smoke checklist (manual or IT): webhook → Kafka → consumer → visible ingest outcome; curl HTTP `/api/v1/ingest` still works
- [x] 5.3 Run `mvn test` for `backend/adapter` and `backend/fm-module`

## Notes

- Frontend tasks: N/A (out of scope).
- Frontend tests: N/A — leave feature-state frontend test status as not applicable when applying.
- Liquibase: skip unless apply proves a schema need (buffer table already exists).
- Out of scope topics: `fm.config-events`, `fm.domain-events`; config sync / health remain HTTP.
- Testcontainers Kafka: not used — repo has no Testcontainers pattern; use `@EmbeddedKafka`.

### Smoke checklist (5.2) — fm-module

Manual / demo after adapter producer + compose Kafka land:

1. Start stack with Kafka (`KAFKA_BOOTSTRAP_SERVERS`), fm-module consumer group `fm-module-ingestion`, topic `fm.raw-events`.
2. Publish (or webhook→adapter) an event envelope → confirm raw event / processed event in console/API.
3. Publish heartbeat envelope → source `lastSuccessAt` / `adapterVersion` update, no new raw events.
4. Curl HTTP debug path still works:
   `curl -X POST "http://localhost:8080/api/v1/ingest" -H "X-Api-Key: demo-source-key" -H "Content-Type: application/json" -d '{"events":[{"externalId":"smoke-1","title":"Smoke","severity":"major","occurredAt":"2026-08-03T12:00:00Z"}]}'`
5. Automated coverage already in place: `RawEventKafkaConsumerTest` (@EmbeddedKafka) + `IngestControllerTest`.

### fm-module `mvn test` (5.3)

- Command: `cd backend/fm-module && mvn test` (JAVA_HOME=JDK 25)
- Result: **BUILD SUCCESS** — Tests run: 112, Failures: 0, Errors: 0, Skipped: 0
- Note: task 2.1 adapter `pom.xml` deps are owned by the adapter agent (fm-module deps done).

### adapter `mvn test` (5.3)

- Command: `cd backend/adapter && mvn test` (JAVA_HOME=`C:\java\jdk25`)
- Result: **BUILD SUCCESS** — Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
- Dual-write (5.1): `WebhookService.deliver` / `BufferRetryWorker` use `RawEventPublisher` only; `FmModuleClient.forwardIngest` retained but unused on ingest path (health still uses HTTP).
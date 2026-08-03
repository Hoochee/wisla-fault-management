# WISLA FM Adapter

Spring Boot service for Push webhook ingestion, pre-filtering, local buffering, and Kafka publish to `fm-module`.

**Stack:** Java 25, Spring Boot 3.4, PostgreSQL 15+, Liquibase, Spring Kafka  
**Port:** 8081  
**Database:** `wisla_fm_adapter`

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Service health (DB, fm-module reachability, buffer count) |
| POST | `/webhook/{sourceKey}` | Accept Push events (`X-Source-Key` or `?sourceKey=`) |
| GET | `/internal/sources/{sourceId}/config` | Cached source config (Bearer internal token) |
| POST | `/internal/probe` | Test connectivity probe (Bearer internal token) |

OpenAPI contract: [`docs/adapter/api.yaml`](../../docs/adapter/api.yaml)

## Quick start

### Prerequisites

- JDK 25
- Maven 3.9+
- PostgreSQL 15+ with database `wisla_fm_adapter`
- Kafka broker reachable for ingest publish (compose provides one)

```sql
CREATE DATABASE wisla_fm_adapter;
```

### Configuration

Copy environment template and adjust values:

```bash
cp .env.example .env
```

Key variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wisla_fm_adapter` | PostgreSQL JDBC URL |
| `FM_MODULE_BASE_URL` | `http://localhost:8080` | fm-module base URL for config sync / health |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap for raw-event publish |
| `KAFKA_RAW_EVENTS_TOPIC` | `fm.raw-events` | Topic for prefiltered ingest envelopes |
| `INTERNAL_SERVICE_TOKEN` | — | Bearer token for `/internal/*` |
| `PORT` | `8081` | HTTP port |

### Run locally

```bash
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8081/health
```

### Build

```bash
mvn clean package
```

### Docker

```bash
docker build -t wisla-fm-adapter .
docker run --rm -p 8081:8081 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/wisla_fm_adapter \
  -e DATABASE_USER=postgres \
  -e DATABASE_PASSWORD=postgres \
  -e FM_MODULE_BASE_URL=http://host.docker.internal:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  wisla-fm-adapter
```

Compose stack (`backend/docker-compose.yaml`) starts Kafka (KRaft), creates topic `fm.raw-events`, and sets `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` for `adapter` / `adapter-2` / `fm-module`. Wait for Kafka health before expecting webhook publish to succeed.

## Behavior

1. **Webhook** — validates API key against `source_config_snapshots`, applies `filter_rules`, publishes a JSON envelope to Kafka topic `fm.raw-events` (Kafka key = `sourceId`). Does **not** HTTP-post to fm-module ingest by default.
2. **Config sync** — on startup and every 5 min pulls `GET /api/v1/internal/sources` from fm-module (`X-Service-Key`) into `source_config_snapshots`.
3. **Buffer** — when Kafka publish fails retryably, payload is stored in `buffered_messages`; background worker retries Kafka publish (not HTTP ingest).
4. **Zabbix payload** — maps `trigger_name`, `event_nseverity`, `event_value=0` (recovery) per [`docs/zabbix-simulator.md`](../../docs/zabbix-simulator.md).
5. **Second instance** — in Docker Compose see `adapter-2` on host port **8083**; connection guide: [`docs/adapter/connect-zabbix-source.md`](../../docs/adapter/connect-zabbix-source.md).
6. **Migrations** — Liquibase changelogs in `src/main/resources/db/changelog/` per `docs/adapter/db.md`.

## Project layout

```
backend/adapter/
├── pom.xml
├── Dockerfile
├── .env.example
└── src/main/java/com/wisla/fm/adapter/
    ├── AdapterApplication.java
    ├── config/
    ├── kafka/
    ├── persistence/
    ├── service/
    └── web/
```

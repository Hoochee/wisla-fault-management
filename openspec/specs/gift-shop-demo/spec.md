# gift-shop-demo Specification

## Purpose

Demo Gift Shop as a Docker Compose overlay (not an FM Maven module): storefront, catalog, checkout, postgres, Prometheus `/metrics`, chaos HTTP; observed only through the adapter.

## Requirements

### Requirement: Gift Shop is a compose overlay under demo/gift-shop

The Gift Shop demo SHALL live in `demo/gift-shop/` as a Docker Compose overlay (storefront, catalog, checkout, postgres, optional cadvisor) and MUST NOT be a Maven module under `backend/`. The main `backend/docker-compose.yaml` MUST remain startable without the overlay.

#### Scenario: Overlay compose files exist

- **GIVEN** the repository after this change
- **WHEN** `demo/gift-shop/docker-compose.yaml` is inspected
- **THEN** it defines `giftshop-storefront` (host 8091), `giftshop-catalog` (8092), `giftshop-checkout` (8093), and `giftshop-postgres` (host 5433)
- **AND** no `backend/gift-shop` Maven module exists

#### Scenario: Base stack does not require Gift Shop

- **GIVEN** only `backend/docker-compose.yaml`
- **WHEN** the FM stack is started
- **THEN** fm-module, adapter, and postgres start without gift-shop services

### Requirement: Demo apps expose Prometheus metrics and chaos HTTP

Each Gift Shop application SHALL expose `GET /metrics` in Prometheus text format. Each application SHALL expose chaos endpoints `POST /chaos/cpu`, `POST /chaos/latency`, `POST /chaos/errors`, `POST /chaos/disk`, `POST /chaos/down`, and `POST /chaos/reset` that change subsequent `/metrics` samples. Applications MUST NOT push events to fm-module or Kafka.

#### Scenario: Metrics endpoint

- **GIVEN** catalog is running
- **WHEN** a client GETs `http://localhost:8092/metrics`
- **THEN** the body is Prometheus exposition including at least `up` and a CPU or JVM CPU metric

#### Scenario: Chaos down flips up

- **GIVEN** checkout is healthy (`up` 1)
- **WHEN** a client POSTs `/chaos/down` with a duration
- **THEN** subsequent `/metrics` reports `up` 0 (or scrape fails) until reset or timeout

#### Scenario: Demo does not ingest into FM

- **GIVEN** the gift-shop application code
- **WHEN** outbound integrations are inspected
- **THEN** there is no client to `POST /api/v1/ingest`, adapter webhooks, or Kafka `fm.raw-events`

### Requirement: Gift Shop is monitored only through the adapter

Operators SHALL observe Gift Shop health only via an fm-module `pull_etl` source whose `parserConfig.targets` point at gift-shop `/metrics` URLs. Seed data SHALL create product `code=giftshop`, configuration items, components, and that pull source.

#### Scenario: Seeded pull source targets demo metrics

- **GIVEN** demo seed applied to `wisla_fm`
- **WHEN** the gift-shop `pull_etl` source is read
- **THEN** `parserConfig.targets` include catalog, checkout, and storefront `/metrics` URLs with corresponding `ciFqdn` values

#### Scenario: Product appears on health heatmap after seed

- **GIVEN** seed applied and adapter scraping
- **WHEN** an operator opens `/health`
- **THEN** product Gift Shop (`code=giftshop`) is listed

### Requirement: cadvisor is best-effort on Windows

The overlay MAY include `giftshop-cadvisor` on host port 8088. App `/metrics` MUST remain sufficient for the demo when cadvisor cannot run.

#### Scenario: Demo works without cadvisor

- **GIVEN** the overlay started with cadvisor omitted or unhealthy
- **WHEN** adapter scrapes application `/metrics` targets
- **THEN** PROBLEM/OK events can still be produced from app metrics
- **AND** the demo is not blocked

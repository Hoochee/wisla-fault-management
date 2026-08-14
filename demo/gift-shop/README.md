# Gift Shop demo overlay

Monitored demo shop for the product-health-graph change. It is **not** an FM service and **not** a Maven module. Apps expose Prometheus `/metrics` and chaos HTTP; they do not push to fm-module, adapter webhooks, or Kafka.

## Start (from repository root)

Compose v5 resolves overlay `build.context` relative to the **first** `-f` file. Merging both files in one command (`-f backend/docker-compose.yaml -f demo/gift-shop/docker-compose.yaml`) looks for `backend/apps` and fails. Use the same project name so Gift Shop joins the FM network:

```bash
docker compose -f backend/docker-compose.yaml up -d --build
docker compose -p backend -f demo/gift-shop/docker-compose.yaml up -d --build
```

The base stack still starts without this overlay (first command only).

| Service | Host port | Role |
|---|---|---|
| `giftshop-storefront` | 8091 | Catalog cards, cart, checkout UI |
| `giftshop-catalog` | 8092 | Product API |
| `giftshop-checkout` | 8093 | Mock payment |
| `giftshop-postgres` | 5433 | Database `giftshop` (not `wisla_fm`) |
| `giftshop-cadvisor` | 8088 | Optional runtime metrics |

Shop UI: http://localhost:8091

### cAdvisor (optional)

App `/metrics` is enough for adapter scrape. cAdvisor is a Compose **profile** so Windows / Docker Desktop is not blocked:

```bash
docker compose -p backend -f demo/gift-shop/docker-compose.yaml --profile cadvisor up -d --build
```

If cAdvisor is omitted or unhealthy, leave it down. Adapter still scrapes `http://giftshop-*:809x/metrics`.

## Seed WISLA FM (`wisla_fm`, after Liquibase 013)

This seed is **not** for giftshop postgres. It creates product `code=giftshop`, CIs, POWER/CPU/HDD/AVAILABILITY components, and a `pull_etl` source whose `parserConfig.targets` point at the three app `/metrics` URLs.

Prerequisite: fm-module has applied `013-product-health.sql` (`product_component` exists).

From the repo root, against the FM database on host port **5432**:

```bash
psql -h localhost -p 5432 -U wisla -d wisla_fm -f demo/gift-shop/seed/wisla-fm-giftshop.sql
```

Password: `wisla`

Via the FM postgres container:

```bash
docker compose -f backend/docker-compose.yaml exec -T postgres psql -U wisla -d wisla_fm < demo/gift-shop/seed/wisla-fm-giftshop.sql
```

PowerShell:

```powershell
Get-Content -Raw demo/gift-shop/seed/wisla-fm-giftshop.sql |
  docker compose -f backend/docker-compose.yaml exec -T postgres psql -U wisla -d wisla_fm
```

Then restart adapter (or wait up to 5 minutes) so it syncs `GET /api/v1/internal/sources`:

```bash
docker compose -f backend/docker-compose.yaml restart adapter
```

After scrape is running, Gift Shop should appear on `/health` in the console (http://localhost:8080).

CI → component mapping:

| CI (`fqdn`) | Component |
|---|---|
| `giftshop-storefront.demo` | POWER |
| `giftshop-catalog.demo` | CPU |
| `giftshop-checkout.demo` | AVAILABILITY (critical) |
| `giftshop-postgres.demo` | HDD (topology; no scrape target) |

## Metrics and chaos

Each app:

- `GET /metrics` — Prometheus text (`up`, `process_cpu_usage`, `process_disk_usage`, …)
- `POST /chaos/cpu|latency|errors|disk|down|reset` — JSON body `{ "durationSeconds": 60 }` (optional)

Examples:

```bash
curl http://localhost:8092/metrics
curl -X POST http://localhost:8093/chaos/down -H "Content-Type: application/json" -d "{\"durationSeconds\":60}"
curl -X POST http://localhost:8092/chaos/cpu -H "Content-Type: application/json" -d "{\"durationSeconds\":60,\"value\":0.96}"
curl -X POST http://localhost:8093/chaos/reset
```

`POST /chaos/down` sets `up 0` on subsequent `/metrics` until reset or timeout. `/health` stays 200 so Compose does not kill the container.

## Layout

```
demo/gift-shop/
  docker-compose.yaml
  apps/                 Node services (SERVICE_ROLE=storefront|catalog|checkout)
  postgres/init.sql     giftshop database only
  seed/wisla-fm-giftshop.sql
```

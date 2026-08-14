# WISLA Fault Management — Agent Overview

## Project Description

`wisla-fault-management` is the Wellink module for receiving, normalizing, processing, and displaying monitoring fault/alarm events (NOC-style console).

Core characteristics:
- Java Spring Boot microservices under `backend/` (PostgreSQL + Liquibase).
- Angular 18 SPA under `frontend/` (served with `fm-module` in Docker).
- Optional Vite + React UI prototype under `prototype/`.
- Product docs and OpenAPI under `docs/`.
- Implemented capabilities tracked in `openspec/specs/`.

Default git branch: **`main`**. Feature branches: `feature/WISLA-<n>` from `origin/main` with `--no-track` (other keys: `feature/<FULL-KEY>`).

Product backlog (deferred work): **[`BACKLOG.md`](BACKLOG.md)** at repo root. Items have a priority (Критический / Высокий / Средний / Низкий). If the user asks what to do next / to take a backlog item, read that file — do not invent scope.

## Module Layout

| Path | Role |
|------|------|
| `backend/fm-module/` | Core FM service: ingest API, processing/rules engine, console/admin REST, JWT auth, serves built Angular static in Docker |
| `backend/adapter/` | External source adapter: push webhook, buffer, retry, heartbeat → fm-module |
| `backend/zabbix-simulator/` | Demo/source simulator for Zabbix-like events |
| `backend/docker-compose.yaml` | Local stack (Postgres, fm-module, adapter, …) |
| `backend/docker/` | Docker assets (e.g. Postgres init) |
| `frontend/` | Angular 18 SPA — console, dashboard, sources, rules, health, admin |
| `prototype/` | Vite + React exploratory UI (not the production SPA) |
| `docs/` | Requirements, architecture, tech stack, page specs, OpenAPI |
| `openspec/` | Spec-driven change workflow (`config.yaml`, `specs/`, `changes/`) |
| `demo/gift-shop/` | Demo overlay (catalog/checkout/storefront + DB); monitored target, not an FM service — added with `product-health-graph` |
| `BACKLOG.md` | Deferred product work for `/build-feature` |

## Tech notes

- **Backend:** Java (README: 21+; `pom.xml` currently sets `java.version` 25), Spring Boot 3.x, JUnit 5, Testcontainers.
- **DB:** PostgreSQL; schema via Liquibase changelogs in each service (`src/main/resources/db/changelog/`).
- **Frontend:** Angular 18; unit tests Vitest (`npm test`); e2e Playwright (`npm run test:e2e`).
- **App URL (Docker):** `http://localhost:8080` (API + frontend static).

## Build / run / test

```bash
# Stack
cd backend && docker compose up -d --build

# Frontend dev
cd frontend && npm install && npm start

# Backend unit/integration
cd backend/fm-module && mvn test
cd backend/adapter && mvn test
cd backend/zabbix-simulator && mvn test

# Frontend unit
cd frontend && npm test

# Frontend e2e (backend must be up)
cd frontend && npm run test:e2e
```

## AI workflow

- OpenSpec: `/opsx:explore`, `/opsx:propose`, `/opsx:apply`, `/opsx:sync`, `/opsx:archive`
- Full feature orchestration: `/build-feature` (see `build-feature/`, `.agents/`, `openspec/TEAM.md`)
- Deferred work: [`BACKLOG.md`](BACKLOG.md) — consult when the user asks for the next task or a backlog item
- Prefer TDD and existing module boundaries; do not invent new top-level services without approval

## Do not

- Overwrite or delete existing `openspec/specs/**` without an intentional sync/archive flow
- Treat `prototype/` as production UI unless the task explicitly says so
- Commit or push unless the user explicitly asks

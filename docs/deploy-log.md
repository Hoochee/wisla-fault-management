# Deploy log — local (WISLA Fault Management)

**Date:** 2026-06-23  
**Target:** local (`.project/deploy.json`)  
**baseUrl:** http://localhost:8080  

## Commands

```bash
cd backend
docker compose up -d --build
docker compose ps
```

### Manual smoke (baseUrl)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/auth/me
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/events
```

### post-deploy-smoke.sh (adapted /api/v1)

```bash
./templates/scripts/post-deploy-smoke.sh http://localhost:8080 /api/v1/auth/me /api/v1/events
```

(WSL/bash unavailable; equivalent checks via `curl.exe`.)

## Docker Compose status

| Service   | Port  | Health   | Notes |
|-----------|-------|----------|-------|
| postgres  | 5432  | healthy  | |
| fm-module | 8080  | healthy  | SPA + API at baseUrl |
| adapter   | 8081  | down     | Exited (1): Liquibase checksum validation on `001-init` |

## Smoke results (http://localhost:8080)

| Check | URL | Expected | Result |
|-------|-----|----------|--------|
| Health | GET /health | 200 | 200 |
| Auth route | GET /api/v1/auth/me | 401 | 401 |
| SPA | GET / | index.html | 200 (Angular shell) |
| Events API | GET /api/v1/events | 401 | 401 |

Gateway smoke: PASSED

Adapter http://localhost:8081/health: FAILED (container not running)

## URLs

- Application: http://localhost:8080
- Health: http://localhost:8080/health
- Adapter: http://localhost:8081/health (when healthy)
- PostgreSQL: localhost:5432

## Local deploy 2026-06-23T15:46:56Z

- **Command:** `docker compose up -d --build` (backend/)
- **baseUrl:** http://localhost:8080
- **Containers:** postgres (healthy), fm-module :8080 (healthy), adapter :8081 (healthy)

### Smoke (fm-module @ http://localhost:8080)

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| GET /health | 200 | 200 | PASS |
| GET /api/v1/auth/me | 401 | 401 | PASS |
| GET / (SPA) | 200 | 200 | PASS |
| GET /api/v1/health/products (no token) | 401 | 401 | PASS |

### Adapter

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| GET /health @ :8081 | 200 | 200 | PASS |

**Overall:** ALL PASS


## Local deploy 2026-06-23T15:56:05Z

- **Command:** `docker compose up -d --build` (backend/)
- **baseUrl:** http://localhost:8080
- **Containers:** postgres (healthy), fm-module :8080 (healthy), adapter :8081 (healthy)

### Smoke (fm-module @ http://localhost:8080)

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| GET /health | 200 | 200 | PASS |
| GET /api/v1/auth/me | 401 | 401 | PASS |
| GET / (SPA) | 200 | 200 | PASS |
| GET /api/v1/health/products (no token) | 401 | 401 | PASS |

### Adapter

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| GET /health @ :8081 | 200 | 200 | PASS |

**Overall:** ALL PASS


## Local deploy 2026-06-23T16:53:08Z

- **Command:** `docker compose up -d --build` (backend/)
- **baseUrl:** http://localhost:8080
- **Note:** fm-module Docker build retried after Maven Central flake; Dockerfile now uses `-Dmaven.test.skip=true` with one retry.
- **Containers:** postgres (healthy), fm-module :8080 (healthy), adapter :8081 (healthy), zabbix-simulator :8082 (healthy)

### Health

| Service | URL | Result |
|---------|-----|--------|
| fm-module | GET http://localhost:8080/health | 200 PASS |
| adapter | GET http://localhost:8081/health | 200 PASS |
| zabbix-simulator | GET http://localhost:8082/health | 200 PASS |

### Smoke (fm-module @ http://localhost:8080)

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| POST /api/v1/auth/login (no body) | 4xx | 400 | PASS |
| POST /api/v1/auth/login (admin) + GET /api/v1/events | 200 | 200 | PASS |

**Overall:** ALL PASS


## Local deploy 2026-06-25T08:46:42Z

- **Command:** `docker compose up -d --build fm-module adapter` (backend/)
- **baseUrl:** http://localhost:8080
- **Feature:** list-running-adapters

### Smoke (fm-module @ http://localhost:8080)

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| GET /health | 200 | 200 | PASS |
| POST /api/v1/auth/login (admin) | 200 | 200 | PASS |
| GET /api/v1/adapters/runtime (Bearer) | 200 | 200 | PASS |

**Overall:** ALL PASS


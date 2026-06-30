# WISLA FM Module (BFF)

Spring Boot 3 BFF for WISLA Fault Management — REST API, ingestion, PostgreSQL, JWT auth.

**Port:** `8080`  
**Database:** PostgreSQL `wisla_fm`  
**Stack:** Java 25, Spring Boot 3.4, Liquibase, JPA

## Prerequisites

- JDK 25+
- Maven 3.9+
- PostgreSQL 15+ with database `wisla_fm`

```sql
CREATE DATABASE wisla_fm;
CREATE USER wisla WITH PASSWORD 'wisla';
GRANT ALL PRIVILEGES ON DATABASE wisla_fm TO wisla;
```

## Run locally

```bash
cd backend/fm-module
mvn spring-boot:run
```

Environment variables (optional):

| Variable | Default |
|----------|---------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wisla_fm` |
| `DATABASE_USER` | `wisla` |
| `DATABASE_PASSWORD` | `wisla` |
| `JWT_SECRET` | dev secret (change in production) |

## MVP endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/health` | none |
| POST | `/api/v1/auth/login` | none |
| GET | `/api/v1/auth/me` | Bearer JWT |
| POST | `/api/v1/ingest?sourceKey={key}` | `X-Api-Key` / Bearer source key |
| GET | `/api/v1/events` | Bearer JWT |

### Seed credentials (dev)

- **User:** `admin` / `admin`
- **Source API key:** `demo-source-key` (query param `sourceKey`)

### Examples

```bash
# Health
curl http://localhost:8080/health

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"admin"}'

# Ingest
curl -X POST "http://localhost:8080/api/v1/ingest?sourceKey=demo-source-key" \
  -H "Content-Type: application/json" \
  -d '{"events":[{"externalId":"evt-1","title":"Disk full","severity":"major","occurredAt":"2026-06-23T10:00:00Z"}]}'
```

## Package structure (bounded contexts)

```
ru.wisla.fm
├── common/          # shared API, security
├── config/          # Spring configuration
├── identity/        # users, roles, JWT auth
├── ingestion/       # adapter intake, raw events
├── processing/      # FM events, lifecycle
├── configuration/   # event sources
├── rules/           # (stub) processing rules
├── cmdb/            # (stub) configuration items
├── health/          # (stub) product health
└── settings/        # (stub) module settings
```

## Docker

```bash
docker build -t wisla-fm-module .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/wisla_fm \
  -e DATABASE_USER=wisla \
  -e DATABASE_PASSWORD=wisla \
  wisla-fm-module
```

## Build

```bash
mvn -DskipTests package
```

Liquibase migrations run automatically on startup (`src/main/resources/db/changelog/`).

## API contract

OpenAPI spec: [`docs/fm-module/api.yaml`](../../docs/fm-module/api.yaml)  
Database schema: [`docs/fm-module/db.md`](../../docs/fm-module/db.md)

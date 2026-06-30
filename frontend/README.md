# WISLA FM — Frontend (Angular 18)

Angular SPA for WISLA Fault Management. Monq-style dark NOC console UI.

**Stack:** Angular 18+, standalone components, Angular Router, HttpClient, JWT in `localStorage`.

**API base:** `/api/v1` (relative). Dev proxy forwards `/api` → `http://localhost:8080`.

## Quick start

```bash
cd frontend
npm install
npm start          # http://localhost:4200, proxy to fm-module :8080
```

Login: `admin` / `admin` (DevDataSeeder).

## Build & deploy to fm-module

```bash
npm run build              # output: dist/frontend/browser/
npm run build:deploy       # build + copy to backend/fm-module/src/main/resources/static/
```

Rebuild backend image after deploy:

```bash
cd backend
docker compose up -d --build fm-module
```

Production: browser requests `{origin}/api/v1/*`; fm-module serves SPA + REST on port 8080.

## Project structure

```
src/app/
  core/
    api/           # ApiClientService, FmApiService, api.models.ts
    auth/          # AuthService, authGuard, authInterceptor
    config/        # mvp-defaults.ts (console polling)
  layout/          # AppLayoutComponent (sidebar, breadcrumbs)
  pages/           # MVP route components
  shared/          # SeverityBadge, StatusBadge, PageHeader, RuleBuilder
```

## Route → API mapping (MVP)

| UI Route | Component | API endpoint(s) | Auth | Status |
|----------|-----------|-----------------|------|--------|
| `/login` | LoginPage | `POST /api/v1/auth/login` | none | **live** |
| `/` | Dashboard | `GET /api/v1/dashboard/summary` | JWT | **live** |
| `/health` | Health | `GET /api/v1/health/products` | JWT | **live** |
| `/health/:productId` | HealthProduct | `GET /api/v1/health/products/{id}` | JWT | **live** |
| `/events/raw` | RawEvents | `GET /api/v1/raw-events` | JWT | **live** |
| `/console` | Console | `GET /api/v1/events`, maps via dashboard; 60s polling | JWT | **live** |
| `/console/:eventId` | EventCard | `GET /api/v1/events/{id}`, `POST /api/v1/events/{id}/actions` | JWT | **live** |
| `/sources` | Sources | `GET /api/v1/sources` | JWT | **live** |
| `/sources/new` | SourceNew | `POST /api/v1/sources` | JWT | **live** |
| `/sources/:id` | SourceEdit | `GET /api/v1/sources/{id}`, `POST /api/v1/sources/{id}/test` | JWT | **live** |
| `/rules` | Rules | `GET /api/v1/rules` | JWT | **live** |
| `/rules/new` | RuleNew | `POST /api/v1/rules` (canvas) | JWT | **live** |
| `/rules/:id` | RuleEdit | `GET/PATCH /api/v1/rules/{id}` (canvas) | JWT | **live** |
| `/admin` | Admin hub | — | JWT | static links |
| `/admin/users` | AdminUsers | `GET /api/v1/admin/users` | JWT | **live** |
| `/admin/roles` | AdminRoles | `GET /api/v1/admin/roles` | JWT | **live** |
| `/admin/ci` | AdminCi | `GET /api/v1/admin/configuration-items` | JWT | **live** |
| `/admin/search-folders` | AdminSearchFolders | maps via dashboard | JWT | **live** (via dashboard) |
| `/settings` | Settings | `GET /api/v1/settings`, `/settings/notifications`, `/settings/integrations`, `/auth/me` | JWT | **live** |
| `/downtime`, `/reports`, … | PostMvpPlaceholder | post-MVP | JWT | placeholder |

## Auth

- JWT stored in `localStorage` key `fm_access_token`
- `Authorization: Bearer {token}` on `/api/v1/*` requests (except login)
- HTTP 401 → redirect to `/login`

## Dev proxy

`proxy.conf.json`:

```json
{ "/api": { "target": "http://localhost:8080" } }
```

Configured in `angular.json` → `serve.options.proxyConfig`.

## Maven integration

After `npm run build:deploy`, fm-module static resources contain the Angular build. `SpaWebConfig` serves `classpath:/static/` with `index.html` fallback for client routes.

## Visual reference

`prototype/` (React) — layout and Monq dark theme reference only (`#1a1d23`–`#252830`, severity colors, sidebar). Rule canvas UX ported to `shared/rule-builder/`.

## Verification (phase B)

```bash
curl http://localhost:8080/health
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"login":"admin","password":"admin"}'
# SPA at http://localhost:8080/
```

Credentials: `admin` / `admin`, demo source key `demo-source-key`.

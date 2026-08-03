# Agent 11: Frontend Test Engineer

## Role

Run frontend unit tests (Vitest) **and** e2e tests (Playwright). Mandatory gate before `review` phase (unless frontend is skipped entirely — see `orchestrator-playbook.md`).

## Constraints

- Unit: `npm test` in `frontend/` (Vitest via `tests/vitest.config.ts`)
- E2E: `npm run test:e2e` in `frontend/` (Playwright via `tests/playwright.config.ts`) — **mandatory**, not conditional on `tasks.md`
- E2E prerequisite: backend must be reachable at the Playwright `baseURL` (default `http://localhost:8080`) before running `npm run test:e2e`:
  - `cd backend && docker compose up -d --build` — full stack (API + built frontend static), **or**
  - run backend services individually and start `cd frontend && npm start` (`ng serve`, proxies API via `proxy.conf.json`), then set `PLAYWRIGHT_BASE_URL=http://localhost:4200`
  - If the backend cannot be brought up, do not silently skip — stop and report the blocker to the orchestrator (`tests.frontend_e2e = "failed"`, reason: backend unavailable)
- Max 3 fix iterations (shared budget across unit + e2e) — escalate to orchestrator
- Do not talk to user

## Inputs

- `frontend/` changes from 10-frontend-engineer
- `tasks.md` frontend test tasks
- Optional: specific Vitest/Playwright filters from tasks.md

## Outputs

- Test run log summary (unit + e2e)
- Fixes to tests or components if needed
- Recommendation: `tests.frontend = passed|failed` and `tests.frontend_e2e = passed|failed`

## Task prompt template

```
You are the Frontend Test Engineer for WISLA Fault Management. Do NOT communicate with the user.

Change: {changeName}
Working directory: frontend

1. Run unit suite: npm test
   Or scoped per tasks.md / vitest filter
2. Ensure backend is reachable (docker compose up -d --build, or ng serve + PLAYWRIGHT_BASE_URL)
   then run e2e suite: npm run test:e2e
   This step is mandatory — do not skip because tasks.md doesn't mention it
3. If failures — fix component or test (prefer fixing real bugs)
4. Repeat until both suites exit 0 (max 3 iterations total)
5. Report: total tests, failures, files fixed, per suite (unit / e2e)

Do NOT mark passed without exit code 0 for BOTH npm test and npm run test:e2e.
If backend cannot be started, report the blocker instead of marking e2e passed/skipped.
```

## Definition of Done

- `npm test` exits 0
- `npm run test:e2e` exits 0
- Frontend tasks for testing marked [x] in tasks.md
- Orchestrator may set `tests.frontend = "passed"` and `tests.frontend_e2e = "passed"`

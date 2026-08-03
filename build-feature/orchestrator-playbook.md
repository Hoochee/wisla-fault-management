# Orchestrator Playbook — Feature State Machine (WISLA Fault Management)

Multi-phase workflow adapted from decomposition-pattern for brownfield Fault Management work.

## Initial state

Created by [bootstrap.md](bootstrap.md) at `openspec/changes/<changeName>/.feature-state.json`.

```json
{
  "phase": "bootstrap",
  "jiraKey": null,
  "changeName": "",
  "branch": "",
  "modules": [],
  "approvals": {
    "scope": false,
    "artifacts": false,
    "readyForPr": false
  },
  "tests": {
    "backend": "pending",
    "frontend": "pending",
    "frontend_e2e": "pending"
  },
  "testFixIterations": {
    "backend": 0,
    "frontend": 0
  },
  "codeReview": {
    "backend": { "status": "pending", "iterations": 0 },
    "frontend": { "status": "pending", "iterations": 0 }
  }
}
```

`tests.frontend_e2e` tracks Playwright e2e separately from Vitest (`tests.frontend`) because they run against different prerequisites (e2e needs a live backend), but both are produced by the same `frontend_tests` phase / same subagent (11-frontend-test-engineer) and gate the **same** transition to `review`. `testFixIterations.frontend` is a shared budget covering fixes to either suite (see [Test fix loop](#test-fix-loop)).

## Phase transitions

| From | Action | To | Gate |
|------|--------|-----|------|
| bootstrap | [bootstrap.md](bootstrap.md): Jira, git branch, `openspec new change` | discovery | auto |
| discovery | Delegate: 01-system-analyst; optional `/opsx:explore` | design | **user:** `approvals.scope = true` |
| design | `/opsx:propose` via propose skill; delegate 05-architect review of design | design | **user:** `approvals.artifacts = true` |
| design | Verify all `applyRequires` artifacts exist | backend | auto when artifacts done + user approved |
| backend | Delegate: 07-backend-engineer × N modules (parallel max 4) | backend_review | auto when all Tasks complete |
| backend_review | Delegate: 09-code-reviewer (`reviewScope: backend`) | backend_tests or backend | **auto:** approved → tests; changes_requested → fix loop |
| backend_tests | Delegate: 08-backend-test-engineer | frontend | **auto:** `mvn test` exit 0 → `tests.backend = passed` |
| frontend | Delegate: 10-frontend-engineer (skip if no frontend in modules/tasks) | frontend_review | auto |
| frontend_review | Delegate: 09-code-reviewer (`reviewScope: frontend`) | frontend_tests or frontend | **auto:** approved → tests; changes_requested → fix loop |
| frontend_tests | Delegate: 11-frontend-test-engineer | review | **auto:** `npm test` exit 0 **and** `npm run test:e2e` exit 0 (both required, see [Frontend test gate](#frontend-test-gate)) → `tests.frontend = passed`, `tests.frontend_e2e = passed` |
| review | User reviews diff; optional manual QA | done | **user:** `approvals.readyForPr = true` |
| done | `/opsx:sync` + `/opsx:archive` | done | notify user |

**Delegate** = Task tool per [delegation/task-tool.md](delegation/task-tool.md).

### Frontend test gate

The `frontend_tests` phase is **only** complete when both of these are green:

1. `npm test` in `frontend/` (Vitest, unit) → `tests.frontend`
2. `npm run test:e2e` in `frontend/` (Playwright, e2e) → `tests.frontend_e2e`

Playwright e2e is **mandatory** whenever frontend is in scope — it is no longer conditional on `tasks.md` mentioning it. Do not advance to `review` with only Vitest green.

**Prerequisite — backend must be running** before `npm run test:e2e` (Playwright's `baseURL` defaults to `http://localhost:8080`):

- `cd backend && docker compose up -d --build` — brings up the full stack (Postgres + services + built frontend static), **or**
- Run backend services individually and `cd frontend && npm start` (`ng serve`, proxies API calls via `proxy.conf.json`), then set `PLAYWRIGHT_BASE_URL=http://localhost:4200` before running `npm run test:e2e`

If the backend cannot be brought up, 11-frontend-test-engineer must report the blocker rather than mark `tests.frontend_e2e` as `passed` or silently `skipped`. Only escalate to the user via the orchestrator (see [Test fix loop](#test-fix-loop)) after exhausting fix iterations.

### Skipping frontend

If `modules` has no `frontend` / Angular SPA and `tasks.md` has no frontend section:

- Set `tests.frontend = "skipped"`
- Set `tests.frontend_e2e = "skipped"`
- Set `codeReview.frontend.status = "skipped"`
- Advance `backend_tests` → `review` directly (skip `frontend_review`)

`tests.frontend_e2e = "skipped"` is valid **only** in this case (no frontend in scope). It must never be `"skipped"` just because `tasks.md` didn't call out e2e explicitly.

### Skipping backend

If change is frontend-only (no `backend/*` modules in tasks):

- Set `tests.backend = "skipped"`
- Advance `design` → `frontend` after artifacts approved

## User-gates

Ask in **one consolidated message** at discovery:

1. Подтвердить scope: модули, non-goals, критерий готовности
2. Уточнить затронуты ли Liquibase/SQL, REST API, Docker Compose, prototype

After `/opsx:propose` completes — show summary of proposal, specs, design, tasks:

> Утвердить артефакты и перейти к реализации?

Before archive:

> Готово к merge/PR? Запустить sync + archive?

## Auto-gates

- All files in `applyRequires` exist and `openspec status` shows apply-ready
- `mvn test` exit 0 for each backend module in scope (run inside module dir)
- `npm test` in `frontend/` exit 0 for frontend (Vitest)
- `npm run test:e2e` in `frontend/` exit 0 for frontend (Playwright) — **required**, not optional; see [Frontend test gate](#frontend-test-gate) for prerequisites

## Test fix loop

When `tests.backend === "failed"` or subagent reports failures:

1. Increment `testFixIterations.backend`
2. If ≤ 3: re-delegate 08-backend-test-engineer with failure log
3. If > 3: ask user how to proceed

Same for `testFixIterations.frontend` with 11-frontend-test-engineer — this single counter covers failures in **either** Vitest or Playwright (they are fixed together, in the same subagent run, before re-checking both).

## Code review loop

After developer (07 or 10) completes, orchestrator advances to `backend_review` or `frontend_review` and delegates **09-code-reviewer**.

### Review verdict parsing

Reviewer returns `VERDICT: approved` or `VERDICT: changes_requested` plus `BLOCKING_FINDINGS` list.

| Verdict | Action |
|---------|--------|
| `approved` | Set `codeReview.<scope>.status = "approved"` → advance to `*_tests` phase |
| `changes_requested` | Increment `codeReview.<scope>.iterations` |

### Fix loop (max 3 iterations)

When `changes_requested`:

1. If `codeReview.<scope>.iterations` ≤ **3**:
   - Set `codeReview.<scope>.status = "changes_requested"`
   - Re-delegate **07-backend-engineer** or **10-frontend-engineer** with blocking findings in prompt
   - After dev Task completes → re-delegate **09-code-reviewer** with `prior_findings` from last review
2. If `codeReview.<scope>.iterations` > **3**:
   - Set `codeReview.<scope>.status = "escalated"`
   - **Stop** — orchestrator reports to user in Russian: summary of unresolved blocking findings, iteration count, options (manual fix / override / abort)

Do not advance to test phase while `codeReview.<scope>.status === "changes_requested"` or `"escalated"` without user decision on escalation.

### Skipping code review

- Frontend-only skip of backend review: set `codeReview.backend.status = "skipped"` when no backend modules in tasks
- No-frontend skip: set `codeReview.frontend.status = "skipped"` (see Skipping frontend above)

## Parallel limits

- Backend implementation: max **4** concurrent Tasks (one per module)
- Backend tests: max **4** concurrent Tasks
- Frontend: sequential (one Task for 10, one for 11)

## OpenSpec commands

Use **`openspec.cmd`** on Windows if `openspec.ps1` is blocked.

| Phase | Command |
|-------|---------|
| discovery | `/opsx:explore` (optional) |
| design | `/opsx:propose <changeName>` |
| backend / frontend | Follow `/opsx:apply` workflow in delegated Tasks |
| done | `/opsx:sync <changeName>` then `/opsx:archive <changeName>` |

## Completion checklist

- [ ] All tasks in `tasks.md` marked done
- [ ] Backend tests green (or skipped)
- [ ] Frontend unit tests green (or skipped)
- [ ] Frontend e2e tests green (or skipped — only when no frontend in scope)
- [ ] User approved ready for PR
- [ ] Delta-specs synced and change archived (when user confirms merge)

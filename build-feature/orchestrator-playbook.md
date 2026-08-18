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
  "riskLevel": null,
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
    "backend": { "status": "pending", "iterations": 0, "reviewers": {} },
    "frontend": { "status": "pending", "iterations": 0, "reviewers": {} }
  }
}
```

`riskLevel` is one of `"L0"`..`"L4"` or `null`. It is set at [bootstrap](bootstrap.md) from the linked Discovery Brief (`openspec/discovery/<slug>/problem.md`) when the backlog item came from `/discovery`. If unknown (`null`), treat it as **`L2`** at review time (see [Risk-gated specialists](#risk-gated-specialists)).

`codeReview.<scope>.reviewers` maps a reviewer id → its verdict for the latest review round:

- `quality` → 09-code-reviewer (always runs)
- `security` → 12-security-reviewer
- `db-api` → 13-db-api-reviewer
- `perf` → 14-performance-reviewer

Each value is `"approved"`, `"changes_requested"`, or `"skipped"` (spawned-but-not-applicable, i.e. the specialist returned `SUMMARY: not applicable`, or gating did not spawn it). The scope `status` is the aggregate (see [Review verdict aggregation](#review-verdict-aggregation)).

`tests.frontend_e2e` tracks Playwright e2e separately from Vitest (`tests.frontend`) because they run against different prerequisites (e2e needs a live backend), but both are produced by the same `frontend_tests` phase / same subagent (11-frontend-test-engineer) and gate the **same** transition to `review`. `testFixIterations.frontend` is a shared budget covering fixes to either suite (see [Test fix loop](#test-fix-loop)).

## Product backlog

Deferred work lives in repo-root [`BACKLOG.md`](../BACKLOG.md).

- User asks «что дальше» / «из беклога» / «следующую задачу» / `/build-feature` without a key — **read `BACKLOG.md`**, propose a `ready` item, do not invent a parallel epic.
- Discovery may **add** parked/blocked items when the user defers work (e.g. extract health microservice). Do not implement backlog items inside an unrelated change.
- After archiving a change, if follow-ups remain, keep them in `BACKLOG.md` (do not only mention them in chat).

## Phase transitions

| From | Action | To | Gate |
|------|--------|-----|------|
| bootstrap | [bootstrap.md](bootstrap.md): Jira, git branch, `openspec new change` | discovery | auto |
| discovery | Delegate: 01-system-analyst; optional `/opsx:explore` | design | **user:** `approvals.scope = true` |
| design | `/opsx:propose` via propose skill; delegate 05-architect review of design | design | **user:** `approvals.artifacts = true` |
| design | Verify all `applyRequires` artifacts exist | backend | auto when artifacts done + user approved |
| backend | Delegate: 07-backend-engineer × N modules (parallel max 4) | backend_review | auto when all Tasks complete |
| backend_review | Delegate **in parallel** (one message, multiple Task calls, max 4): 09-code-reviewer (`reviewScope: backend`) + risk-gated specialists (12/13/14 per [gating](#risk-gated-specialists)) | backend_tests or backend | **auto:** all reviewers approved/skipped → tests; any changes_requested → fix loop |
| backend_tests | Delegate: 08-backend-test-engineer | frontend | **auto:** `mvn test` exit 0 → `tests.backend = passed` |
| frontend | Delegate: 10-frontend-engineer (skip if no frontend in modules/tasks) | frontend_review | auto |
| frontend_review | Delegate **in parallel** (one message, multiple Task calls, max 4): 09-code-reviewer (`reviewScope: frontend`) + risk-gated specialists (12/13/14 per [gating](#risk-gated-specialists)) | frontend_tests or frontend | **auto:** all reviewers approved/skipped → tests; any changes_requested → fix loop |
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

After developer (07 or 10) completes, the orchestrator advances to `backend_review` or `frontend_review` and delegates a **parallel, risk-gated review stage**: **09-code-reviewer** (Code Quality — always) plus the applicable specialist reviewers (12 Security, 13 DB/API, 14 Performance/FinOps). All reviewers are spawned **in a single message with multiple Task calls** (max 4 concurrent per [Parallel limits](#parallel-limits)); each returns the **same verdict contract** scoped to its axis.

### Risk-gated specialists

Which reviewers run is a function of `state.riskLevel` (see [`../discovery/references/risk-levels.md`](../discovery/references/risk-levels.md); `null` ⇒ treat as `L2`) **and** whether the change actually touches each specialist's area. Discovery **classifies** the level; `/build-feature` **enforces** the review depth here. Always assign the **deepest applicable coverage**: a specialist is also spawned regardless of level whenever its trigger is clearly present, because scope signals can exceed the assigned level.

| Level | Code Quality (09) | Security (12) | DB/API (13) | Performance (14) |
|-------|:---:|:---:|:---:|:---:|
| **L0** | ✅ | — | — | — |
| **L1** | ✅ | — | — | — |
| **L2** (default) | ✅ | — | — | — |
| **L3** | ✅ | if touches area¹ | if touches area² | — |
| **L4** | ✅ | if touches area¹ | if touches area² | ✅ |

**Trigger override (any level):** spawn the specialist even below its level when its trigger is clearly present.

- ¹ **Security (12)** — change touches `common/security`, auth/JWT/roles, API-key filters, new endpoints handling untrusted input, or persistence of sensitive data.
- ² **DB/API (13)** — change touches `backend/*/src/main/resources/db/changelog/`, any `*.api.yaml`/controllers/DTOs, or persistence adapters (any Liquibase/OpenAPI change ⇒ spawn regardless of level).
- **Performance (14)** — `L4`, or change touches event-processing hot paths, batch/ingest, or queries on large tables.

A spawned specialist that finds its area not actually touched returns `VERDICT: approved` / `SUMMARY: not applicable` — record it as `skipped` in `reviewers`. Spawning uniformly (each prompt self-gates via its "Applies when" rule) keeps orchestration simple.

**L4 — mandatory human decision:** for `riskLevel === L4`, the orchestrator must **not** leave the review phase on auto-gate alone. After all reviewers approve, pause and require an explicit human decision (independent verification / sign-off) in Russian before advancing to the test phase — per L4 rigor in `risk-levels.md` ("do not proceed without an explicit human decision").

### Review verdict aggregation

Each reviewer returns `VERDICT: approved` or `VERDICT: changes_requested` plus a `BLOCKING_FINDINGS` list. Record each reviewer's result in `codeReview.<scope>.reviewers[<id>]` (`approved` | `changes_requested` | `skipped`), then aggregate:

| Aggregate condition | Scope `status` | Action |
|---------------------|----------------|--------|
| Every spawned reviewer is `approved` or `skipped` (not applicable) | `approved` | Advance to `*_tests` phase (for `L4`, after the mandatory human decision) |
| Any reviewer returned `changes_requested` | `changes_requested` | Increment `codeReview.<scope>.iterations`; run the fix loop with the **union** of all reviewers' `BLOCKING_FINDINGS` |

### Fix loop (max 3 iterations)

When aggregate is `changes_requested`:

1. If `codeReview.<scope>.iterations` ≤ **3**:
   - Set `codeReview.<scope>.status = "changes_requested"`
   - Re-delegate **07-backend-engineer** or **10-frontend-engineer** once with the **union of BLOCKING_FINDINGS** from all reviewers (each finding keeps its `ref: quality|spec|standard|security|db-api|perf` tag)
   - After the dev Task completes → re-run the **same parallel review stage** (09 + the same spawned specialists) with `prior_findings` from the last round; each reviewer verifies only its own prior blocking findings
2. If `codeReview.<scope>.iterations` > **3**:
   - Set `codeReview.<scope>.status = "escalated"`
   - **Stop** — orchestrator reports to user in Russian: summary of unresolved blocking findings grouped by reviewer, iteration count, options (manual fix / override / abort)

Do not advance to the test phase while `codeReview.<scope>.status === "changes_requested"` or `"escalated"` without a user decision on escalation.

### Skipping code review

- Frontend-only skip of backend review: set `codeReview.backend.status = "skipped"` when no backend modules in tasks
- No-frontend skip: set `codeReview.frontend.status = "skipped"` (see Skipping frontend above)

## Parallel limits

- Backend implementation: max **4** concurrent Tasks (one per module)
- Backend tests: max **4** concurrent Tasks
- Frontend implementation/tests: sequential (one Task for 10, one for 11)
- Review stage (`backend_review` / `frontend_review`): the risk-gated reviewers (09 + applicable specialists 12/13/14) run **in parallel**, max **4** concurrent Tasks — at most 4 reviewers exist, so all spawned reviewers fit in one batch

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

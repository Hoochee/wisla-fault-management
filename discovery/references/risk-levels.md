# Risk Levels L0–L4 — Discovery (WISLA Fault Management)

Risk-adaptive gates adapted to Fault Management. During the `risk` phase the Discovery Orchestrator (or the Product Discovery agent's recommendation) assigns one level **L0–L4**. The level is recorded in `.discovery-state.json` (`riskLevel`) and in the Discovery Brief, and it **informs how deep the downstream `/build-feature` review/testing should go** — which review specialists and CI depth are needed once those are wired up (see [`build-feature/orchestrator-playbook.md`](../../build-feature/orchestrator-playbook.md)). Discovery only **classifies**; `/build-feature` **enforces**.

Assign the **highest** level any trigger matches (a change is only as low-risk as its riskiest part).

| Level | Scope | Downstream rigor in `/build-feature` |
|-------|-------|--------------------------------------|
| **L0** | Docs, comments, trivial internal cleanup; no behavior change | Minimal — sanity check only; no new tests required |
| **L1** | Low-risk internal code (small refactor, dev-only tooling), no external contract or data change | Tests for touched code + lint |
| **L2** | Normal production feature or bug fix (default for most FM work) | Unit / integration tests, CI, code-reviewer gate (`.agents/09-code-reviewer.md`) |
| **L3** | Customer-visible **or** touches data / contracts / persistence | Regression + security check + rollback path + explicit user approval; both backend and frontend review/tests where in scope |
| **L4** | Critical infra / data integrity / authentication | Independent verification + traceability + sign-off; do not proceed without an explicit human decision |

## FM-specific L3 triggers

Assign **L3** (at minimum) when discovery indicates any of:

- **Liquibase / SQL schema change** — new changeset, column/table/index, altered constraint, persistent state (`backend/*/src/main/resources/db/changelog/`).
- **REST / OpenAPI contract change** — new/changed endpoint, request/response shape, `docs/**/api.yaml` (esp. anything not backward compatible).
- **Event-processing correctness** — dedup, PROBLEM/OK lifecycle, health calculation, storm suppression — a defect reaches the on-duty operator in production.
- **Notifications** — real delivery (email / Telegram / push), suppression logic, escalation.
- **Customer-visible SPA behavior** — console / event card / rules / sources / health / admin flows a client sees.

## FM-specific L4 triggers

Assign **L4** when discovery touches:

- **Auth / JWT / roles** — authentication, authorization, session, role-based access (`common/security`, API-key filters).
- **Data integrity across services** — anything that can corrupt or desync persistent state (e.g. extracting a service that owns tables, cross-service event ownership).
- **Critical infra** — ingest path the whole product depends on, or a change that, if wrong, takes the product down.

## Notes

- **Default is L2** for a normal feature/bug fix. Do not inflate: a doc-only or dev-only change is L0/L1 even if the surrounding feature is important.
- The risk level is orthogonal to backlog **priority** (Критический / Высокий / Средний / Низкий): priority = business urgency; risk level = how careful delivery must be. A Низкий-priority auth tweak can still be L4.
- This level feeds a future RISK_REGISTER downstream; keep the rationale short and concrete in the brief.

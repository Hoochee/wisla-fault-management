# Bootstrap — Feature start

Run at the beginning of `/build-feature` when `.feature-state.json` does not exist or `phase === "bootstrap"`.

## Inputs

- Jira key: `WISLA-<number>` (**required** for branch naming when a ticket exists; other project keys allowed if the ticket uses them)
- Or backlog ID `FM-<n>` from [`BACKLOG.md`](../BACKLOG.md) when there is no Jira ticket — use as the workflow key instead of Jira (do **not** invent a Jira URL)
- Change name: kebab-case (derived from Jira title, backlog title, or user description)
- Base branch: always `main`

## Steps

1. **Read Jira** (if a Jira key is provided)
   - MCP `user-chrome-devtools`: navigate to `https://support.wellink.ru/browse/<KEY>`
   - `take_snapshot` — extract title, description, acceptance criteria
   - If the user picked a backlog item `FM-<n>` — read title/scope from [`BACKLOG.md`](../BACKLOG.md), skip Jira, do not invent a browse URL
   - If both Jira and `FM-<n>` are missing — ask the user before creating a branch

2. **Derive names**
   - `changeName`: kebab-case, e.g. `console-column-sort`
   - `branch`: `feature/WISLA-<number>` (Jira; number from key only — no kebab suffix; use full key if prefix ≠ WISLA) or `feature/FM-<n>` (backlog without Jira)

3. **Git branch**
   ```bash
   git fetch origin main
   git switch -c feature/WISLA-<number> --no-track origin/main
   # or, backlog without Jira:
   git switch -c feature/FM-<n> --no-track origin/main
   ```
   **Critical:** always use `--no-track`. Without it, the new branch inherits upstream `origin/main`, and IDE push becomes `feature/...:main` (rejected on protected `main`).

   Do **not** set upstream to `main`. First push of the feature branch (only when user asks to push):
   ```bash
   git push -u origin HEAD
   ```
   That creates `origin/feature/WISLA-<number>` or `origin/feature/FM-<n>` and sets the correct upstream.

   Do not commit unless user explicitly asks.

4. **OpenSpec change**
   ```bash
   openspec new change "<changeName>"
   ```
   If change already exists — ask user to continue or pick another name.

5. **Resolve risk level (`riskLevel`)**
   - If the item originates from `/discovery` — an `FM-<n>` with a linked Discovery Brief at `openspec/discovery/<slug>/problem.md` (also recorded in `openspec/discovery/<slug>/.discovery-state.json` as `riskLevel`) — read its **Risk level (L0–L4)** and set `state.riskLevel` to that value. See [`../discovery/references/risk-levels.md`](../discovery/references/risk-levels.md).
   - If there is no Discovery Brief / the level is unknown — set `"riskLevel": null`. It is treated as **`L2`** at review time. If the change later looks L3/L4 (Liquibase/OpenAPI, auth/security, event-processing correctness), the orchestrator may ask the user to confirm the level before the review phase.

6. **Create state file**

   Write `openspec/changes/<changeName>/.feature-state.json`:

   ```json
   {
     "phase": "discovery",
     "jiraKey": "WISLA-12345",
     "changeName": "my-feature",
     "branch": "feature/WISLA-12345",
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

   For a backlog item without Jira, set `"jiraKey": "FM-<n>"` and `"branch": "feature/FM-<n>"` (same field, no browse URL).

   `riskLevel` is `"L0"`..`"L4"` from the Discovery Brief, or `null` (⇒ treated as `L2` at review time). It selects which parallel review specialists run — see `orchestrator-playbook.md#risk-gated-specialists`.

   `codeReview.<scope>.reviewers` starts empty and is filled during the review phase with reviewer verdicts (`quality`/`security`/`db-api`/`perf` → `approved|changes_requested|skipped`).

   `tests.frontend_e2e` tracks Playwright e2e (mandatory alongside Vitest whenever frontend is in scope — see `orchestrator-playbook.md#frontend-test-gate`).

7. Advance `phase` to `discovery` and report to user:
   - branch name
   - change path: `openspec/changes/<changeName>/`
   - next step: discovery questions or delegate System Analyst

## Re-entry

If `.feature-state.json` exists, read `phase` and continue from orchestrator-playbook — do not recreate branch or change.

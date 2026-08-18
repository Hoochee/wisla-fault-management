# Delegation: task-tool (Cursor — WISLA Fault Management)

Use the **Task** tool to delegate to specialized agents in `.agents/`.

## Rules

- `subagent_type`: `generalPurpose` for implementation; `shell` for `mvn test` / `npm test`; `explore` for read-only codebase analysis
- Max **4 parallel** Task invocations for backend modules
- Pass full content of `.agents/NN-{name}.md` plus feature context in the prompt
- Subagents must NOT communicate with the user
- Include paths: `openspec/changes/<changeName>/`, affected `backend/*` / `frontend/` modules

## Context block (always include)

```
Change: {changeName}
State file: openspec/changes/{changeName}/.feature-state.json
Branch: {branch}
Jira: {jiraKey}
Modules: {modules[]}
Read: proposal.md, specs/, design.md, tasks.md in the change directory
```

## Templates

### System Analyst (discovery)

```
Task:
  subagent_type: generalPurpose
  prompt: |
    {contents of .agents/01-system-analyst.md}

    {context block}
    Jira summary: ...
```

### Architect (design review / propose support)

```
Task:
  subagent_type: generalPurpose
  prompt: |
    {contents of .agents/05-architect.md}

    {context block}
```

### Backend Engineer (parallel per module)

```
Task:
  subagent_type: generalPurpose
  prompt: |
    {contents of .agents/07-backend-engineer.md}

    Module: backend/fm-module
    {context block}
```

Launch up to 4 Tasks; queue remaining modules from `state.modules`.

### Review stage — parallel, risk-gated (after backend or frontend implementation)

The `backend_review` / `frontend_review` phase spawns **Code Quality (09) plus the applicable specialists (12/13/14) in ONE message, multiple Task calls** (max 4 concurrent). Which specialists run is decided by `state.riskLevel` + area triggers — see [orchestrator-playbook.md#risk-gated-specialists](../orchestrator-playbook.md#risk-gated-specialists) and [`../../discovery/references/risk-levels.md`](../../discovery/references/risk-levels.md). All reviewers are read-only (enforced by their prompts) and emit the **same verdict contract**.

#### Code Reviewer — Code Quality (always)

```
Task:
  subagent_type: generalPurpose
  readonly: true
  prompt: |
    {contents of .agents/09-code-reviewer.md}

    reviewScope: backend | frontend
    {context block}
    prior_findings: (optional — from last review when re-reviewing)
```

#### Security Reviewer (id `security`) — gated

```
Task:
  subagent_type: generalPurpose
  readonly: true
  prompt: |
    {contents of .agents/12-security-reviewer.md}

    reviewScope: backend | frontend
    riskLevel: {riskLevel}
    {context block}
    prior_findings: (optional — from last review when re-reviewing)
```

#### DB / API Contract Reviewer (id `db-api`) — gated

```
Task:
  subagent_type: generalPurpose
  readonly: true
  prompt: |
    {contents of .agents/13-db-api-reviewer.md}

    reviewScope: backend | frontend
    riskLevel: {riskLevel}
    {context block}
    prior_findings: (optional — from last review when re-reviewing)
```

#### Performance / FinOps Reviewer (id `perf`) — gated

```
Task:
  subagent_type: generalPurpose
  readonly: true
  prompt: |
    {contents of .agents/14-performance-reviewer.md}

    reviewScope: backend | frontend
    riskLevel: {riskLevel}
    {context block}
    prior_findings: (optional — from last review when re-reviewing)
```

**Spawn rule:** always spawn 09; spawn 12/13/14 per the gating table. Each specialist self-gates via its "Applies when" rule and returns `SUMMARY: not applicable` (record as `skipped`) when its area is not touched — so spawning uniformly is safe.

**Aggregation:** parse `VERDICT` from every response and record it in `codeReview.<scope>.reviewers[<id>]`. Scope `status = approved` only if every spawned reviewer is `approved`/`skipped`. If **any** is `changes_requested` and `codeReview.<scope>.iterations` ≤ 3 — re-delegate 07 or 10 once with the **union** of all `BLOCKING_FINDINGS`, then re-run this whole parallel stage. If > 3 — escalate to user. For `riskLevel === L4`, require an explicit human decision before advancing even when all reviewers approve.

### Backend Test Engineer

```
Task:
  subagent_type: shell
  prompt: |
    {contents of .agents/08-backend-test-engineer.md}

    Modules: {modules[]}
    {context block}
```

### Frontend Engineer

```
Task:
  subagent_type: generalPurpose
  prompt: |
    {contents of .agents/10-frontend-engineer.md}

    {context block}
```

### Frontend Test Engineer

```
Task:
  subagent_type: shell
  prompt: |
    {contents of .agents/11-frontend-test-engineer.md}

    {context block}
    Working directory: frontend
```

## After subagent completes

Orchestrator updates `openspec/changes/<changeName>/.feature-state.json` and reports progress to user in Russian.

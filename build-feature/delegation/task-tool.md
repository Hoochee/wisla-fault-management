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

### Code Reviewer (after backend or frontend implementation)

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

Parse `VERDICT` from response. If `changes_requested` and `codeReview.<scope>.iterations` ≤ 3 — re-delegate 07 or 10 with blocking findings, then re-run 09. If > 3 — escalate to user.

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

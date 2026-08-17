---
name: build-feature
description: >-
  Orchestrates WISLA Fault Management feature development from a Jira task or backlog ID to tested implementation.
  Runs phases via specialized agents with gates and OpenSpec integration.
  Use when user invokes /build-feature or asks to start a feature with WISLA-* or FM-*.
disable-model-invocation: true
---

# Build Feature — WISLA Fault Management (Cursor)

**Delegation:** `task-tool` (Cursor Task, max 4 parallel backend modules)  
**OpenSpec:** `/opsx:explore`, `/opsx:propose`, `/opsx:apply`, `/opsx:sync`, `/opsx:archive`

## Instructions

1. Load [build-feature/SKILL.core.md](../../build-feature/SKILL.core.md)
2. If the user asks for the next task / backlog item, or `/build-feature` has no key — read [`BACKLOG.md`](../../BACKLOG.md). A backlog ID `FM-<n>` is an acceptable substitute for a Jira key when the user picks a backlog item (no fake Jira URL).
3. If no `.feature-state.json` for the change — run [build-feature/bootstrap.md](../../build-feature/bootstrap.md)
4. Load [build-feature/delegation/task-tool.md](../../build-feature/delegation/task-tool.md)
5. Load [build-feature/orchestrator-playbook.md](../../build-feature/orchestrator-playbook.md)
6. Agent prompts: [.agents/](../../.agents/)

All paths relative to repository root.

## Invocation examples

```
/build-feature WISLA-12345
/build-feature FM-1
/build-feature console-column-sort
/build-feature продолжи фичу console-column-sort
```

## Phases (summary)

`bootstrap` → `discovery` → `design` → `backend` → `backend_review` → `backend_tests` → `frontend` → `frontend_review` → `frontend_tests` → `review` → `done`

State file: `openspec/changes/<changeName>/.feature-state.json`

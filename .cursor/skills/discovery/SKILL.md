---
name: discovery
description: >-
  Runs upstream Product Discovery for WISLA Fault Management: turns a Jira epic or a free-text idea
  into a reviewed Discovery Brief (problem.md) and a ready FM-<n> backlog item for /build-feature.
  Two agents (Discovery Orchestrator + Product Discovery), one human gate, no branch and no OpenSpec change.
  Use when the user invokes /discovery or asks to start discovery for a WISLA-* epic or a product idea.
disable-model-invocation: true
---

# Discovery — WISLA Fault Management (Cursor)

**Delegation:** `task-tool` (Cursor Task) — the orchestrator delegates the Discovery Brief to the Product Discovery agent  
**Seam:** `/discovery` → `BACKLOG.md` (`FM-<n>`, `ready`) → `/build-feature FM-<n>`

## Instructions

1. Load [discovery/SKILL.core.md](../../discovery/SKILL.core.md)
2. If no `.discovery-state.json` for the slug — run [discovery/bootstrap.md](../../discovery/bootstrap.md) (intake + slug + state; **no** git branch, **no** OpenSpec change)
3. Load [discovery/playbook.md](../../discovery/playbook.md)
4. Load [discovery/AGENTS.md](../../discovery/AGENTS.md)
5. Agent prompts: [.agents/discovery-00-orchestrator.md](../../.agents/discovery-00-orchestrator.md), [.agents/discovery-01-product-discovery.md](../../.agents/discovery-01-product-discovery.md)
6. Backlog seam: [`BACKLOG.md`](../../BACKLOG.md)

All paths relative to repository root.

## Execution model

Execute **one phase step per invocation** unless the user asks to continue. Only the orchestrator talks to the user; the Product Discovery agent runs via the Task tool and surfaces Open Questions to the orchestrator. Halt-on-ambiguity; user communication at the gate is in Russian; do not commit unless the user explicitly asks.

## Invocation examples

```
/discovery WISLA-12345
/discovery дежурный не видит историю передачи смены
/discovery continue
```

## Phases (summary)

`bootstrap` → `discovery` → `risk` → `review` → `backlog` → `done`

State file: `openspec/discovery/<slug>/.discovery-state.json`

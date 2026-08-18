# Discovery Playbook — Phase State Machine (WISLA Fault Management)

Upstream PDLC (Product Discovery) that feeds a `ready` backlog item to `/build-feature`. Two agents only: the Discovery Orchestrator (user-facing) and the Product Discovery agent (never talks to the user).

## Initial state

Created by [bootstrap.md](bootstrap.md) at `openspec/discovery/<slug>/.discovery-state.json`.

```json
{
  "phase": "bootstrap",
  "jiraKey": null,
  "slug": "",
  "title": "",
  "source": "jira|manual",
  "riskLevel": null,
  "approvals": { "discovery": false },
  "backlogItem": null
}
```

| Field | Meaning |
|-------|---------|
| `phase` | Current phase (see below) |
| `jiraKey` | `WISLA-<n>` (or other key) when `source: "jira"`, else `null` |
| `slug` | kebab-case artifact slug; artifact root `openspec/discovery/<slug>/` |
| `title` | short human title |
| `source` | `"jira"` (intake from Wellink browse URL) or `"manual"` (free-text idea) |
| `riskLevel` | `"L0"`…`"L4"` per [references/risk-levels.md](references/risk-levels.md); `null` until the `risk` phase |
| `approvals.discovery` | the single user gate — brief approved |
| `backlogItem` | the allocated `FM-<n>` key once written to `BACKLOG.md`; `null` before |

## Phases

`bootstrap` → `discovery` → `risk` → `review` → `backlog` → `done`

## Phase transitions

| From | Action | To | Gate |
|------|--------|-----|------|
| bootstrap | [bootstrap.md](bootstrap.md): intake (Jira via chrome-devtools MCP, else manual idea) → derive slug + title → create `openspec/discovery/<slug>/` + state | discovery | auto |
| discovery | Orchestrator asks the discovery questions ([references/question-bank.md](references/question-bank.md), one consolidated message, Russian) → delegate Product Discovery agent → write `problem.md` (Discovery Brief) | risk | auto when brief written |
| risk | Classify L0–L4 per [references/risk-levels.md](references/risk-levels.md); record `riskLevel` in state + brief | review | auto |
| review | Orchestrator presents the brief summary in Russian; asks to approve problem + KPI + hypotheses + risks + risk level | backlog | **user:** `approvals.discovery = true` |
| backlog | Orchestrator writes/updates the `FM-<n>` item in [`BACKLOG.md`](../BACKLOG.md) (see [Backlog write](#backlog-write)); set `backlogItem` | done | auto |
| done | Orchestrator tells the user the `FM-<n>` is `ready` and suggests `/build-feature FM-<n>` | done | notify user |

**Delegate** = Task tool → [.agents/discovery-01-product-discovery.md](../.agents/discovery-01-product-discovery.md) (`subagent_type: generalPurpose`). Subagents must NOT communicate with the user.

## User gate

Ask in **one consolidated message** at `review` (Russian):

> Утвердить Discovery Brief? Проверьте: постановку проблемы (JTBD), KPI / метрики успеха, гипотезы, риски и уровень риска (L0–L4). Ответьте «утверждаю» или что поправить.

On «утверждаю» → set `approvals.discovery = true`, advance to `backlog`. On revisions → re-delegate the Product Discovery agent (or edit the brief) and re-present.

The Product Discovery agent surfaces its `Open Questions` back to the orchestrator. If any are material (block problem / KPI / scope), the orchestrator asks the user **before** the gate — halt-on-ambiguity, no silent assumptions.

## Backlog write

On the `backlog` phase, the orchestrator writes the item into [`BACKLOG.md`](../BACKLOG.md) per [`.cursor/rules/backlog.mdc`](../.cursor/rules/backlog.mdc):

- **Key**: next free `FM-<n>` (never reuse a number). Set `state.backlogItem = "FM-<n>"`.
- **Статус**: `ready`.
- **Приоритет**: honest — Критический / Высокий / Средний / Низкий (do not inflate; if it neither blocks the product nor is needed for demo/pilot, it is not Критический/Высокий).
- **Change (черновик)**: the discovery `slug`.
- **Модули** / **Зависит от**: from the brief's proposed backlog item, when known.
- **Откуда**: the Jira key when `source: "jira"`, else `/discovery`.
- Reference the Discovery Brief: `openspec/discovery/<slug>/problem.md`.
- Never invent a Jira URL for an `FM-*` key.

The item format mirrors existing `BACKLOG.md` entries (a `| | |` table with Ключ / Статус / Приоритет and optional Модули / Зависит от / Change (черновик) / Откуда, then **Что сделать** and optional **Не делать**).

## Completion checklist

- [ ] `openspec/discovery/<slug>/problem.md` exists and covers all template sections
- [ ] `riskLevel` set (L0–L4) with rationale in the brief
- [ ] User approved the brief (`approvals.discovery = true`)
- [ ] `FM-<n>` item written to `BACKLOG.md` (`ready`, honest priority, Discovery Brief referenced)
- [ ] `state.backlogItem` set; `phase === "done"`
- [ ] User told the next command is `/build-feature FM-<n>`
- [ ] No git branch, no OpenSpec change, nothing under `openspec/specs/**`

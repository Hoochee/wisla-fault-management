---
name: /discovery
id: discovery
category: Workflow
description: Upstream product discovery — turn a Jira epic or an idea into a reviewed Discovery Brief and a ready FM-<n> backlog item for /build-feature
---

Run upstream PDLC (Product Discovery) in WISLA Fault Management. Turns a Jira Epic/Issue **or** a free-text product idea into a reviewed **Discovery Brief** (`problem.md`) + an enriched **BACKLOG.md** item (`FM-<n>`, status `ready`), which the user then implements via `/build-feature FM-<n>`.

Two-workflow topology: `/discovery` (upstream) → `BACKLOG.md` (`FM-<n>`, the typed seam) → `/build-feature` (delivery). This command does **NOT** create a git branch and does **NOT** create an OpenSpec change — that happens later in `/build-feature` bootstrap.

**Input**: Jira key (`WISLA-<n>` — Epic or Issue), a free-text idea, or `continue` to resume from `.discovery-state.json`.

**Load skill**: Follow `.cursor/skills/discovery/SKILL.md` and `discovery/SKILL.core.md`.

---

## Steps

1. **Resolve intake**
   - If a Jira key is given — read Jira via chrome-devtools MCP (`https://support.wellink.ru/browse/<KEY>`); set `source: "jira"`
   - If free-text — capture it as the raw idea; set `source: "manual"`
   - Derive a kebab-case `slug` and short `title` from the Jira title or the idea
   - If resuming (`continue`) — read `openspec/discovery/<slug>/.discovery-state.json`

2. **Bootstrap** (if `phase === "bootstrap"` or no state file)
   - Do **NOT** create a git branch and do **NOT** run `openspec new change`
   - Create `openspec/discovery/<slug>/` and write `.discovery-state.json` per `discovery/bootstrap.md`
   - Set `phase: discovery`

3. **Execute current phase** (ONE step unless the user asks to continue)
   - Read `discovery/playbook.md`
   - **discovery**: ask the discovery questions from `discovery/references/question-bank.md` (Russian, one message) → Task → `.agents/discovery-01-product-discovery.md` writes `problem.md`
   - **risk**: classify L0–L4 per `discovery/references/risk-levels.md`; record `riskLevel`
   - **review**: show the brief summary → user gate `approvals.discovery`
   - **backlog**: write the `FM-<n>` item into `BACKLOG.md` (`ready`, honest priority, brief referenced); set `state.backlogItem`
   - **done**: tell the user `FM-<n>` is ready; suggest `/build-feature FM-<n>`

4. **Update state**
   - After each step: update `openspec/discovery/<slug>/.discovery-state.json`
   - Report progress to the user in Russian

---

## Guardrails

- Only the orchestrator talks to the user; the Product Discovery agent runs via the Task tool only and surfaces Open Questions to the orchestrator
- Halt-on-ambiguity: ask the user when intent / scope / success is unclear — no silent assumptions
- Do not skip the `approvals.discovery` gate
- Do **not** create a git branch, do **not** create an OpenSpec change, do **not** write Spec/PRD/ADR, do **not** touch `openspec/specs/**`
- Do not commit or push unless the user explicitly asks
- Backlog: next free `FM-<n>` (never reuse), honest priority, never invent a Jira URL for `FM-*`

---

## Output

After each invocation, summarize:
- Current `phase` and `slug`
- Source (Jira key or manual idea) and risk level (once set)
- What was done and what gate is next
- Exact next command: `/discovery continue`, or — when done — `/build-feature FM-<n>`

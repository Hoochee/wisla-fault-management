---
name: /build-feature
id: build-feature
category: Workflow
description: Orchestrate a Fault Management feature from Jira through OpenSpec to tested implementation
---

Orchestrate incremental feature work in WISLA Fault Management using multi-agent workflow with gates.

**Input**: Jira key (`WISLA-12345` or other), change name (kebab-case), or feature description. Optional: `continue` to resume from `.feature-state.json`.

**Load skill**: Follow `.cursor/skills/build-feature/SKILL.md` and `build-feature/SKILL.core.md`.

---

## Steps

1. **Resolve feature identity**
   - If Jira key given — read Jira via chrome-devtools MCP (`https://support.wellink.ru/browse/<KEY>`)
   - Derive `changeName` (kebab-case) from title or user input
   - If resuming — read `openspec/changes/<changeName>/.feature-state.json`

2. **Bootstrap** (if `phase === "bootstrap"` or no state file)
   - `git fetch origin main`
   - Create git branch from `origin/main` with `--no-track`: `git switch -c feature/WISLA-<n> --no-track origin/main` (never inherit upstream `main`)
   - `openspec new change "<changeName>"` if change does not exist
   - Write `.feature-state.json` per `build-feature/bootstrap.md`
   - Set `phase: discovery`

3. **Execute current phase** (ONE step unless user asks to continue)
   - Read `build-feature/orchestrator-playbook.md`
   - **discovery**: questions from `build-feature/references/question-bank.md` → user gate `approvals.scope`
   - **design**: run `/opsx:propose` workflow → user gate `approvals.artifacts`
   - **backend**: Task → `.agents/07-backend-engineer.md` (parallel per module, max 4)
   - **backend_review**: Task → `.agents/09-code-reviewer.md` (`reviewScope: backend`) → fix loop max 3 → escalate
   - **backend_tests**: Task → `.agents/08-backend-test-engineer.md` → auto-gate `mvn test`
   - **frontend**: Task → `.agents/10-frontend-engineer.md` (skip if no frontend)
   - **frontend_review**: Task → `.agents/09-code-reviewer.md` (`reviewScope: frontend`) → fix loop max 3 → escalate
   - **frontend_tests**: Task → `.agents/11-frontend-test-engineer.md` → auto-gate `npm test`
   - **review**: show summary → user gate `approvals.readyForPr`
   - **done**: `/opsx:sync` + `/opsx:archive` when user confirms

4. **Update state**
   - After each subagent: update `openspec/changes/<changeName>/.feature-state.json`
   - Report progress to user in Russian

---

## Guardrails

- Only orchestrator talks to user; subagents via Task tool only
- Do not skip user-gates (`scope`, `artifacts`, `readyForPr`)
- Do not mark tests passed without exit code 0
- Max 3 test-fix iterations per test phase — then escalate
- Max 3 code-review fix iterations per implementation phase — then escalate to user
- Use `openspec.cmd` on Windows if PowerShell blocks scripts
- Do not commit unless user explicitly asks
- Do not overwrite existing `openspec/specs/**` outside sync/archive flow

---

## Output

After each invocation, summarize:
- Current `phase` and `changeName`
- Branch name
- What was done and what gate is next
- Exact next command: `/build-feature continue` or user action needed

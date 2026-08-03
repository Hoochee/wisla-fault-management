# Карта агентов — Build Feature (WISLA Fault Management)

Главная точка входа: `/build-feature`. Только **00-orchestrator** (через skill) общается с пользователем.

| # | Файл | Роль | Артефакты |
|---|------|------|-----------|
| 00 | [.agents/00-orchestrator.md](../.agents/00-orchestrator.md) | Координация, gates | `.feature-state.json` |
| 01 | [.agents/01-system-analyst.md](../.agents/01-system-analyst.md) | Scope из Jira | summary для proposal |
| 05 | [.agents/05-architect.md](../.agents/05-architect.md) | Design по модулям | `design.md`, specs |
| 07 | [.agents/07-backend-engineer.md](../.agents/07-backend-engineer.md) | Java Spring Boot | код в `backend/*` |
| 08 | [.agents/08-backend-test-engineer.md](../.agents/08-backend-test-engineer.md) | Maven Surefire | `mvn test` |
| 09 | [.agents/09-code-reviewer.md](../.agents/09-code-reviewer.md) | Code review (Quality + Standards + Spec) | verdict → fix loop |
| 10 | [.agents/10-frontend-engineer.md](../.agents/10-frontend-engineer.md) | Angular 18 SPA | `frontend/` |
| 11 | [.agents/11-frontend-test-engineer.md](../.agents/11-frontend-test-engineer.md) | Vitest (+ Playwright если в tasks) | `npm test` |

Документация: [SKILL.core.md](SKILL.core.md) · [orchestrator-playbook.md](orchestrator-playbook.md)

Не используются (greenfield-only): 02-tech-advisor, 03-prototype-designer, 04-prototype-reviewer, 06-api-designer.

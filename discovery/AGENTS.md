# Карта агентов — Discovery (WISLA Fault Management)

Главная точка входа: `/discovery`. Апстрим PDLC (Product Discovery) **перед** `/build-feature`. Только **discovery-00-orchestrator** (через skill) общается с пользователем.

## Топология двух воркфлоу

```
/discovery  ──►  BACKLOG.md (FM-<n>, status: ready)  ──►  /build-feature FM-<n>
 (upstream)         (типизированный шов)                    (delivery)
```

- `/discovery` превращает Jira Epic/Issue **или** свободную идею в проверенный **Discovery Brief** (`problem.md`) + обогащённый пункт `FM-<n>` в [`BACKLOG.md`](../BACKLOG.md) со статусом `ready`.
- Пункт беклога `FM-<n>` — **типизированный шов** между воркфлоу: `/discovery` пишет его, `/build-feature` его читает и реализует.
- `/discovery` **не** создаёт git-ветку, **не** создаёт OpenSpec change, **не** пишет Spec/PRD/ADR. Всё это — работа `/build-feature` (bootstrap + `/opsx:propose` + `.agents/05-architect.md`). Discovery останавливается на уровне проблема / KPI / гипотезы / риски.

## Агенты

| # | Файл | Роль | Артефакты |
|---|------|------|-----------|
| 00 | [.agents/discovery-00-orchestrator.md](../.agents/discovery-00-orchestrator.md) | Координация, human gate, состояние, запись `FM-<n>` в беклог | `.discovery-state.json`, пункт `BACKLOG.md` |
| 01 | [.agents/discovery-01-product-discovery.md](../.agents/discovery-01-product-discovery.md) | Discovery Brief из Jira epic + Q&A: JTBD, KPI, гипотезы, риски, risk level | `problem.md` |

Только оркестратор говорит с пользователем. Product Discovery-агент surface-ит открытые вопросы **оркестратору**, не пользователю.

## Документация

- [SKILL.core.md](SKILL.core.md) — правила оркестратора, порядок загрузки
- [bootstrap.md](bootstrap.md) — первый запуск: intake + slug + state
- [playbook.md](playbook.md) — фазовая машина, таблица переходов, правила записи в беклог
- [references/question-bank.md](references/question-bank.md) — набор discovery-вопросов (Russian)
- [references/risk-levels.md](references/risk-levels.md) — уровни риска L0–L4
- [templates/problem.md](templates/problem.md) — шаблон Discovery Brief
- Беклог: [`BACKLOG.md`](../BACKLOG.md) · delivery-воркфлоу: [`build-feature/AGENTS.md`](../build-feature/AGENTS.md)

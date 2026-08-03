# Question Bank — Fault Management Feature Discovery

Orchestrator uses these during **discovery** phase. Adapt to Jira context; skip irrelevant ones. Batch in 1–2 messages max.

> Jira keys default to Wellink `WISLA-*`; if the ticket uses another project key, keep the full key as given.

## Task context

1. Какой номер Jira (WISLA-* или другой ключ) и краткая суть задачи?
2. Это bugfix, новая функция или рефакторинг?
3. Есть ли жёсткий дедлайн / целевой релиз?
4. Критерии приёмки из Jira / docs — полные или нужно уточнить?

## Modules

5. Какие модули затронуты? (`backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, `frontend/`, редко `prototype/`)
6. Нужны ли изменения в Docker Compose / демо-стенде (`backend/docker-compose*.yaml`)?
7. Затрагивается ли Angular SPA (`frontend/`) — консоль, правила, источники, health, админка?
8. Нужны ли правки только в React-прототипе (`prototype/`), без production SPA?

## Data and API

9. Нужны ли изменения БД / Liquibase (`backend/*/src/main/resources/db/changelog/`)?
10. Меняется ли REST API — breaking или обратно совместимо? Обновить `docs/**/api.yaml`?
11. Есть ли влияние на ingest adapter → fm-module (API key, buffer, heartbeat)?
12. Меняется ли визуальный rule canvas / notify / enable toggle?

## Scope boundaries

13. Что явно **не** входит в задачу (non-goals)?
14. Можно ли ограничиться минимальным diff без рефакторинга соседнего кода?
15. Есть ли связанные задачи / зависимости в docs или openspec/specs?

## Testing

16. Какие автотесты обязательны: `mvn test` в каком backend-модуле, `npm test` (Vitest), Playwright e2e?
17. Нужны ли ручные сценарии для QA по `docs/demo-script.md` / `docs/pages-spec.md`?

## Follow-up triggers

- «Правила / canvas» → `frontend` rule-builder + `fm-module` rules runtime?
- «Источник / адаптер» → `adapter` + Liquibase + sync config в fm-module?
- «Консоль / колонки» → Angular console pages + API sort/filter?
- «Симулятор Zabbix» → `zabbix-simulator` + demo compose override?
- «Миграция БД» → номер changelog, откат, данные на стенде?

After answers — delegate 01-system-analyst to draft scope summary for `approvals.scope` gate (before `/opsx:propose`).

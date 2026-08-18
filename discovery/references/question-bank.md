# Question Bank — Fault Management Product Discovery

The Discovery Orchestrator uses these during the **discovery** phase. Adapt to the Jira epic / idea context; skip irrelevant ones. Batch into **one consolidated message** (Russian), max two. These are upstream product questions (problem / users / KPI / hypotheses / risks) — **not** implementation questions (those belong to `/build-feature` discovery, see [`build-feature/references/question-bank.md`](../../build-feature/references/question-bank.md)).

> Jira keys default to Wellink `WISLA-*`; if the epic uses another project key, keep the full key as given. For a free-text idea there is no Jira — работаем от идеи пользователя.

## Контекст / проблема

1. Какая проблема или возможность? Что болит сейчас и у кого?
2. Что заставило поднять это сейчас (триггер): жалоба клиента, инцидент, конкурент (напр. Monq), продажа/пилот?
3. Что происходит, если ничего не делать? Есть ли обходной путь сегодня?
4. Как это связано с уже реализованным (`openspec/specs/`, `docs/`) — расширение, замена, новое?

## Кто пользователи и JTBD

5. Кто основной пользователь / роль? (дежурный NOC, админ, инженер источников, продавец/маркетинг, руководитель смены…)
6. Кто ещё затронут (stakeholders): смежные команды, интеграции, клиент?
7. JTBD-формулировка: **кто** / **когда** (в какой ситуации) / **хочет** (что сделать) / **чтобы** (какой результат)?

## Метрики успеха / KPI

8. Как поймём, что стало лучше? Какая измеримая метрика (время реакции, шум в консоли, MTTR, доля покрытых продуктов, конверсия пилота…)?
9. Известен ли текущий baseline и целевое значение (baseline → target)?
10. За какой горизонт ожидаем эффект (демо, пилот, релиз)?

## Гипотезы

11. Какая ключевая гипотеза: «если сделаем X, то получим Y»? Как проверим (демо, метрика, интервью)?
12. Какие есть альтернативные решения и почему предпочитаем это?

## Риски и ограничения

13. Что может пойти не так? (данные, безопасность/JWT, схема БД/Liquibase, контракт REST/OpenAPI, корректность обработки событий, доставка notify)
14. Есть ли зависимость от других задач / активных change / внешних систем?
15. Есть ли ограничения: сроки, лицензия/коммерция, совместимость, персональные данные?
16. Насколько это «дорого откатить», если решение окажется неверным? (влияет на уровень риска L0–L4 — см. [risk-levels.md](risk-levels.md))

## Что вне scope

17. Что явно **не** входит (non-goals)? Что осознанно откладываем?
18. Можно ли ограничиться минимальным первым шагом (MVP) вместо полного решения?

## Открытые вопросы

19. Что ещё неясно и мешает сформулировать проблему / критерий успеха? (halt-on-ambiguity — не додумывать молча)
20. Кто может дать недостающие данные (владелец продукта, клиент, аналитика)?

## Follow-up triggers (FM-специфика)

- «Схема БД / persistent state» → флаг L3+, вход в build-feature с Liquibase changeset.
- «REST / OpenAPI контракт» → флаг L3+, синхронизация `docs/**/api.yaml`.
- «Авторизация / JWT / роли» → флаг L3/L4, регрессия безопасности.
- «Корректность обработки событий / dedup / notify» → флаг L3, влияет на дежурного в проде.
- «Только внутренняя чистка / доки» → вероятно L0/L1, минимальная строгость.

After answers — delegate [.agents/discovery-01-product-discovery.md](../../.agents/discovery-01-product-discovery.md) to draft the Discovery Brief (`problem.md`) for the `approvals.discovery` gate.

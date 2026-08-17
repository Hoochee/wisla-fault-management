## Context

Сейчас единственный UI весов — inline-панель `<app-component-weight-editor>` («Веса компонентов») на `/health/:productId` (`frontend/src/app/pages/health/health-product-page.component.html`). Редактор живёт в `frontend/src/app/shared/health/component-weight-editor.component.ts`: вес 0–100, `influenceType`, чекбоксы КЕ. Admin сохраняет через `saveComponentWeights()` → `FmApiService.patchProduct` → `PATCH /api/v1/admin/products/{id}` с `{ components }`. Не-админ сейчас видит ту же панель read-only (`[editable]="isAdmin()"`). Пункта весов в sidebar и на `/settings` нет. Формула и маппинг severity→health не меняются.

Админ в SPA — это `AuthService.isAdmin` (`permissions` содержит `'admin'` после probe `GET /admin/products`), не строка роли «Администратор». Дежурный и специалист в UI неотличимы: `isAdmin() === false`.

Страница уже умеет overlay-модалки (редактирование продукта, picker КЕ, удаление) — стили `.overlay` / `.modal` (ширина `min(32rem, 92vw)`) в `health-product-page.component.scss`. Playwright `frontend/tests/e2e/health-graph.spec.ts` ищет всегда видимый `app-component-weight-editor`.

Capability `product-health-graph` не меняется: карточка по-прежнему рисует snapshot с `GET /api/v1/health/products/{id}`. Delta только у `health-product-crud` (где живёт UI весов).

## Goals / Non-Goals

**Goals:**

- Убрать всегда видимую панель редактора с карточки продукта (в т.ч. read-only панель у не-админа).
- Admin: кнопка на `/health/:productId` открывает модалку с тем же редактором и Save; после успешного PATCH веса остаются после reload (колонка «Вес» и повторное открытие модалки).
- Non-admin: кнопки нет; колонка «Вес» в таблице компонентов остаётся read-only.
- Тот же PATCH; обновить Vitest и Playwright под новый UX.

**Non-Goals:**

- Настраиваемые веса severity / event → health.
- Новый пункт sidebar / `/settings`.
- Backend, Liquibase, Docker, OpenAPI, `prototype/`, смена формулы здоровья, новые Angular-маршруты.
- Новый top-level модуль/сервис и новый Angular standalone component для модалки (не требуется).

## Decisions

### D1. Frontend-only, тот же PATCH

Меняется только Angular SPA. Новых модулей нет: `state.modules` = `frontend/`. Не добавляем сервисы в `backend/`, не трогаем `prototype/`.

Интеграция (без изменения контракта):

```text
SPA ──REST JWT──► fm-module :8080  PATCH /api/v1/admin/products/{id}
```

Контракт `components: [{code, name, weight, influenceType, criticalThreshold, ciIds}]` без изменений. `saveComponentWeights()` остаётся на `HealthProductPageComponent`. Adapter → fm-module ingest не затрагивается.

**ADR-001 hexagonal checklist: N/A** — нет нового и нет материально изменённого backend-поведения. По пунктам:

1. Use cases / inbound ports — N/A
2. Inbound adapters — N/A
3. Outbound ports — N/A
4. Outbound adapter implementations — N/A
5. Infrastructure wiring — N/A
6. Spring-free use-case tests — N/A

Dependency direction (domain / application / adapters / `infrastructure/config`) не документируем: backend не меняется.

Альтернатива: endpoint настроек. Отклонена — API уже есть.

### D2. Кнопка на карточке, не меню настроек

Кнопка «Веса компонентов» только при `isAdmin()`, в `.top-actions` рядом с «Редактировать» / «Удалить продукт». Маршрут `/health/:productId` не меняется. Sidebar и `/settings` не трогаем.

Таблица «Компоненты» с колонкой «Вес» остаётся на карточке для всех ролей.

Альтернатива: пункт `/settings` или sidebar (формулировка FM-13). Отклонена — пользователь утвердил кнопку + модалку. Альтернатива: кнопка в шапке карточки «Компоненты». Допустима при реализации, если `.top-actions` перегружен; поведение то же.

### D3. Модалка страницы + существующий редактор

Не вводить UI-библиотеку модалок и не выносить `ComponentWeightModalComponent`. Повторить паттерн `@if (editModalOpen())` / CI picker:

- Signal `weightModalOpen`.
- Overlay + `.modal` шире таблицы редактора (модификатор, например `.modal-wide`; дефолтные 32rem узки). Клик по overlay / «Отмена» закрывает без PATCH.
- Внутри — существующий `<app-component-weight-editor>` с теми же `@Input` / `@Output`. В модалке `[editable]="true"`: не-админ модалку не открывает.
- Save только на редакторе (клиентская проверка суммы весов > 0). На рамке модалки — «Отмена», без второго Save.
- Успешный PATCH: `weightModalOpen.set(false)` и `reloadCard()`. Ошибка PATCH (400/403/сеть): модалка остаётся открытой, `weightError` по-прежнему уходит в `[error]` редактора.
- Пока модалка закрыта, `app-component-weight-editor` **не в DOM** (`@if (weightModalOpen())`, не CSS `hidden`) — иначе Playwright/Vitest могут найти панель на карточке.
- Повторное открытие заново копирует drafts из `@Input() components` (текущий setter редактора). Перед открытием сбрасывать `weightError`.

Альтернатива: вынести `ComponentWeightModalComponent`. Не обязательно: страница уже хостит модалки. Можно вынести, если шаблон станет нечитаемым; это не новый top-level модуль.

### D4. Тесты только frontend, TDD

Нет задач `mvn test`. TDD: Vitest red → реализация → `npm test` green → Playwright.

`HealthProductPageComponent` тяжёлый (Sankey, heatmap, sidebar). Vitest:

- TestBed + моки `AuthService` (`isAdmin`, `currentUser` truthy, чтобы `ngOnInit` сразу звал `reloadCard`) и `FmApiService` (`getProduct`, `getProductHistory`, `getProductAdmin`, `patchProduct` через `of(...)` / `throwError`).
- `ActivatedRoute` с `productId`; `provideRouter([])`.
- Stub/override тяжёлых children (`MonqHealthGraphComponent`, heatmap, CI sidebar, operative panel), **живой** `ComponentWeightEditorComponent`.
- Не ходить в HTTP.

Playwright: поправить `health-graph.spec.ts` — на загрузке редактора нет; admin открывает модалку; тот же PATCH; после SPA-reload колонка «Вес» и повторное открытие модалки показывают новое значение.

### Touch points (`frontend/`)

| Файл | Действие |
|---|---|
| `health-product-page.component.ts` | `weightModalOpen`; close on success; не менять URL/`patchProduct` |
| `health-product-page.component.html` | убрать inline editor; кнопка admin; overlay `@if` |
| `health-product-page.component.scss` | `.modal-wide` (или эквивалент) |
| `component-weight-editor.component.ts` | reuse, без смены API |
| `frontend/tests/unit/health-product-weight-modal.test.ts` | новый Vitest |
| `frontend/tests/e2e/health-graph.spec.ts` | кнопка + модалка + таблица |
| `docs/pages-spec.md` | UX карточки; OpenAPI не трогать |

Не трогать: `fm-api.service.ts`, `app.routes.ts`, backend, Liquibase, Docker, `prototype/`.

### Module split

| Модуль | Что меняется |
|---|---|
| `frontend/` | кнопка + overlay; хостинг `ComponentWeightEditorComponent`; Vitest; Playwright |
| `docs/` | `pages-spec.md` (описание UX). Не deployable, не новый модуль продукта |
| `backend/fm-module` | нет |
| `backend/adapter` | нет |
| `backend/zabbix-simulator` | нет |
| `prototype/` | нет |

### Integration points

```text
HealthProductPageComponent
  → (admin click) weight modal + ComponentWeightEditorComponent
  → saveComponents
  → FmApiService.patchProduct(id, { components })
  → PATCH /api/v1/admin/products/{id}
  → reload GET /api/v1/health/products/{id}
```

### Liquibase / REST

Нет миграций. REST без изменений: тот же PATCH; omit `components` на других PATCH по-прежнему не трогает слоты; 403 non-admin; 400 если все веса 0.

### Spec → test mapping

| Scenario (`health-product-crud`) | Test task |
|---|---|
| Inline weight editor is hidden on the product card | 1.1 (Vitest, admin, modal closed) + 4.1 (e2e, editor absent until button) |
| Admin opens modal and saves weights | 1.2 (Vitest: PATCH + modal closes) + 4.1 (e2e: table «Вес» after reload + reopen) |
| Admin cancels without saving | 1.3 |
| Non-admin has no weight editor | 1.1 (Vitest, `isAdmin=false`) |

## Risks / Trade-offs

- [E2e ищет inline editor] → обновить Playwright до кнопки + модалки в том же change.
- [Черновик после Cancel] → закрытие убирает редактор из DOM; reopen берёт persisted `@Input`.
- [Модалка уже узкая 32rem] → расширить только weight-modal.
- [Двойной Save: редактор и футер] → Save только на редакторе; на рамке модалки — «Отмена».
- [TestBed страницы тянет d3/Sankey] → stub children; иначе хрупкие unit-тесты.
- [Ошибка PATCH] → не закрывать модалку, иначе `weightError` пропадёт вместе с редактором.

## Migration Plan

1. Выкатить frontend вместе с обновлённым e2e. Backend не деплоить отдельно.
2. Rollback: откат SPA; API совместим.
3. Feature-flag не нужен.

## Open Questions

Нет блокирующих. Кнопка по умолчанию в `.top-actions`; допустимо перенести в шапку «Компоненты» без смены контракта.

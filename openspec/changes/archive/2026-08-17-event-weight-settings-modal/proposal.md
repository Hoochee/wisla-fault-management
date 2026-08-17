## Why

На карточке продукта `/health/:productId` редактор весов компонентов (`app-component-weight-editor`, «Веса компонентов») всегда виден inline и занимает место в операторском экране здоровья. FM-13: спрятать настройку весов из постоянного UI. Утверждённый UX — кнопка администратора, открывающая модалку с тем же редактором.

## What Changes

- Убрать постоянно видимую панель `<app-component-weight-editor>` с карточки продукта.
- **Администратор:** на `/health/:productId` кнопка открывает модалку с существующим редактором (вес 0–100, тип влияния, чекбоксы КЕ) и **Сохранить**.
- Сохранение по-прежнему через `PATCH /api/v1/admin/products/{id}` (`patchProduct` / `saveComponentWeights()`). Контракт API не меняется.
- **Не-администратор:** кнопки нет; колонка «Вес» в таблице компонентов остаётся read-only.
- Обновить Playwright e2e `frontend/tests/e2e/health-graph.spec.ts`, который сейчас ищет inline `app-component-weight-editor`.
- Обновить `docs/pages-spec.md`: редактор весов — в модалке, не постоянно на карточке.

### Non-goals

- Настраиваемые веса severity / event → health (маппинг остаётся hardcoded; вне scope).
- Новый пункт в sidebar, `/settings` или меню настроек.
- Изменения backend, Liquibase, Docker, `prototype/`, OpenAPI.
- Смена формулы здоровья (Monq).
- Новые маршруты Angular.

## Capabilities

### New Capabilities

- Нет. Поведение остаётся внутри существующей capability `health-product-crud`.

### Modified Capabilities

- `health-product-crud`: требование **Health product UI edits component weights** — admin редактирует веса в модалке по кнопке, а не в всегда видимой панели; не-админ не видит кнопку и не может редактировать.

## Impact

- **`frontend/`**: `health-product-page` (кнопка + overlay/modal), переиспользование `ComponentWeightEditorComponent`; Vitest (open/save/cancel, admin vs non-admin); Playwright `health-graph.spec.ts`. Маршрут `/health/:productId` без смены URL.
- **`docs/pages-spec.md`**: описание редактора весов на карточке продукта.
- **Не затрагиваются:** `backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, Liquibase, OpenAPI (`docs/fm-module/api.yaml`), `prototype/`, `demo/gift-shop/`.

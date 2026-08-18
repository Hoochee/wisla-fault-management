## Why

Консоль NOC умеет только **принять в работу себе** (`take` → `in_progress` + assignee = текущий пользователь) и **закрыть**. Дежурный не может подтвердить аварию, не беря её; прокомментировать из UI (API `comment` уже есть); назначить коллеге с записью в журнал; временно спрятать шум, не сбрасывая PROBLEM. Нужны те же duty-действия, что в оперцентре Monq — ack / comment / assign / silence — без эскалаций и ITSM.

## What Changes

- Расширить существующий `POST /api/v1/events/{id}/actions` действиями `ack`, `assign`, `silence` (без новых REST-ресурсов). `take` / `close` / `comment` без breaking change.
- **ack:** статус не меняется; колонки `acknowledged_at` / `acknowledged_by_user_id`; журнал `ack`; un-ack нет; повторный ack обновляет timestamp.
- **comment:** кнопка в SPA на уже существующий API (`comment` обязателен).
- **assign:** только `assigned_user_id` на активного пользователя из `GET /api/v1/admin/users?active=true`; статус **не** становится `in_progress` (это делает `take`).
- **silence:** `silenced_until = now + N` минут; UI-пресеты 15 / 30 / 60; API принимает любой `silenceMinutes > 0`; авто-снятие по времени; `unsilence` в MVP нет.
- `GET /api/v1/events`: query `includeSilenced` (default `false`) скрывает `silenced_until > now()`. `GET /{id}` всегда возвращает событие. Health/dashboard не меняются.
- Processing: при дедуп-повторе silenced события `repeatCount` / `lastRepeatAt` растут, `NotificationPort` / `PushNotificationPort` не вызываются, пока `silencedUntil` в будущем.
- Liquibase `014-event-duty-actions.sql` на `events`. OpenAPI `docs/fm-module/api.yaml` синхронизировать.
- UI: кнопки на карточке `/console/:eventId` **и** на нижней панели выбранной строки `/console`. Без bulk и без контекстного меню.
- Hexagonal (ADR-001, option A): inbound port `PerformEventActionUseCase`; silence-check в уже hexagonal `ProcessRawEventBatchService`.

### Non-goals

- Матрицы эскалации, on-call, downtime-календарь, согласования, ITSM, bulk, WebSocket, `defer`/`maintenance`, `assigned_team_id`, починка `mapId` в `listEvents`, health/dashboard counters, adapter, simulator, Kafka, `prototype/`, отдельный top-level сервис, кнопка unsilence / un-ack.

## Capabilities

### New Capabilities

- `event-duty-ack`: подтверждение без смены статуса; колонки ack; журнал; 409 на closed/archived.
- `event-duty-comment-ui`: кнопка «Комментарий» на карточке и в консоли на существующий `action=comment`.
- `event-duty-assign`: назначение коллеге без `in_progress`; `take` по-прежнему self + in_progress.
- `event-duty-silence`: скрытие на N минут, фильтр списка, suppress notify/push, авто-снятие по `silencedUntil`.
- `console-duty-actions`: кнопки ack/comment/assign/silence на карточке и нижней панели выбранной строки; индикация и подписи журнала.

### Modified Capabilities

- `architecture-hexagonal-processing`: второй inbound port `PerformEventActionUseCase` (сейчас единственный вход — `ProcessRawEventBatchUseCase`); `EventController` остаётся inbound adapter в `processing.api` (исключение D7); silence-check в `ProcessRawEventBatchService`.
- `rules-notify-block`: исполнение notify/push на уже сохранённом событии пропускается, пока действует silence; canvas notify/push не меняется.

## Impact

- **`backend/fm-module`:** domain `Event` (поля ack/silence); `PerformEventActionUseCase` + service; расширить `EventStorePort` / persistence mapper; новые outbound ports журнал и справочник пользователей; `EventController` / `EventActionRequest` / `EventDto`; `EventQueryService.listEvents` + `includeSilenced`; `ProcessRawEventBatchService` suppress; Liquibase `014-event-duty-actions.sql`; `EventControllerTest` + Spring-free use-case tests; `ProcessingConfig`.
- **`frontend/`:** `event-card-page`, `console-page` (панель выбранного), `FmApiService.performEventAction` / `listUsers(?active=true)`, `api.models` (`EventActionType`, поля Event). Маршруты `/console` и `/console/:eventId` без смены URL.
- **Docs:** `docs/fm-module/api.yaml`; строка в `docs/pages-spec.md` (панель действий).
- **Тесты:** `cd backend/fm-module && mvn test`; `cd frontend && npm test`; Playwright e2e карточки и консоли.
- **Не затрагиваются:** `backend/adapter`, `backend/zabbix-simulator`, `prototype/`, Docker Compose, Kafka ingest.

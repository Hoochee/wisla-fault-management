## Context

Консоль NOC (`/console`, `/console/:eventId`) умеет только `take` и `close`. API `POST /api/v1/events/{id}/actions` в `EventController` делегирует в слоёный `processing.service.EventActionService` (`@Service`, JPA, `Authentication`, Jackson): switch `take` / `close` / `comment`, иначе 400 `Unsupported action`. `EventActionRequest` — только `action` + `comment`. Домен `processing.domain.Event` и `EventJpaEntity` не хранят ack/silence. `GET /api/v1/events` (`EventQueryService.buildSpec`) фильтрует status/severity/sourceId/ciId и **не** скрывает шум. `ProcessRawEventBatchService` после merge всегда вызывает `NotificationPort` / `PushNotificationPort`.

Processing уже в пилоте ADR-001 (`docs/adr/ADR-001-hexagonal-architecture.md`): inbound `ProcessRawEventBatchUseCase`, outbound ports, `ProcessingConfig`. Консольный REST остаётся исключением D7: `EventController` в `processing.api`, ArchUnit transport rule не покрывает `processing.api`. Утверждённый scope FM-10 (пользователь «утверждаю»): option A — извлечь `PerformEventActionUseCase`; silence-check в уже hexagonal batch service.

Интеграция runtime без изменений ingest:

```text
Angular SPA (frontend/)  --JWT REST-->  fm-module :8080
  GET  /api/v1/events[?includeSilenced]
  GET  /api/v1/events/{id}
  POST /api/v1/events/{id}/actions
  GET  /api/v1/admin/users?active=true

adapter  --ingest-->  fm-module  (не меняется)
```

Модули: `backend/fm-module`, `frontend/`. Adapter / simulator / prototype не трогаем.

## Goals / Non-Goals

**Goals:**

- Duty-действия ack / comment UI / assign / silence на том же `POST .../actions` без breaking change `take`/`close`.
- Liquibase колонки ack/silence; список активных скрывает текущий silence; карточка по id всегда доступна.
- Повтор silenced события мержится, notify/push подавляются до истечения `silencedUntil`.
- UI на карточке и нижней панели выбранной строки; пресеты silence 15/30/60; справочник `GET /api/v1/admin/users?active=true`.
- ADR-001 option A: `PerformEventActionUseCase` + Spring-free тесты; D7: контроллер остаётся в `processing.api` и вызывает порт.

**Non-Goals:**

- Эскалации, on-call, downtime, approvals, ITSM, bulk, WebSocket, `defer`/`maintenance`, `unsilence`/`un-ack`, `assigned_team_id`, починка `mapId`, health/dashboard counters, Kafka, adapter, simulator, prototype, новый deployable, перенос `EventController` в `adapter/in`, widening ArchUnit transport на весь `processing`.

## Decisions

### D1. Один POST actions, без новых ресурсов

Новые `action`: `ack`, `assign`, `silence`. Body additive: `assignedUserId` (UUID), `silenceMinutes` (integer). OpenAPI `EventActionType` дополняется; `defer`/`maintenance` остаются в enum, код по-прежнему 400 (дыра контракта не закрывается реализацией).

HTTP-коды как сейчас через `GlobalExceptionHandler`:

| Условие | Исключение | HTTP |
|---|---|---|
| Событие / пользователь не найден | `NotFoundException` (inbound adapter мапит domain miss) | 404 |
| Validation: нет `assignedUserId`, неактивный пользователь, пустой comment, `silenceMinutes` ≤ 0, unknown action | `IllegalArgumentException` | 400 |
| ack/silence/take на `closed`/`archived`; close уже closed | `IllegalStateException` | 409 |

Альтернатива: отдельные `POST .../ack`. Отклонена — scope требует reuse существующего ресурса.

### D2. Семантика действий (locked)

| Action | Status | Прочие поля | Журнал |
|---|---|---|---|
| `ack` | без изменения | `acknowledgedAt=now`, `acknowledgedByUserId=actor`; повторный ack обновляет timestamp; un-ack нет | `ack` |
| `comment` | без изменения | текст обязателен | `comment` |
| `assign` | без изменения (**не** `in_progress`) | `assignedUserId` обязателен, активный пользователь | `assign` |
| `silence` | без изменения, PROBLEM живой | `silencedUntil=now+N`, `silencedByUserId=actor`; N любой `> 0`; unsilence нет | `silence` |
| `take` | `in_progress` | assignee = actor, `takenAt=now` | `take` |
| `close` | `closed` | `closedAt=now` | `close` |

`PATCH /events/{id}` с `assignedUserId` не используем как duty-assign (нет журнала).

### D3. Список vs карточка vs health

`GET /api/v1/events`: `includeSilenced` default **false**. Скрывать строки, где `silenced_until IS NOT NULL AND silenced_until > now()`. Истёкший silence снова в списке (сравнение с `now()` на запросе, без job). `includeSilenced=true` — без этого предиката. `GET /{id}` без фильтра. Health/dashboard/active counts **не** меняются. Отдельного списка «скрытые» нет.

`mapId` по-прежнему игнорируется `listEvents` (не FM-10).

### D4. ADR-001 hexagonal — option A (зависимость и чеклист)

**Направление зависимостей:** `domain` ← `application` (ports + services) ← `adapter` / `infrastructure`. Domain и application **не** импортируют Spring, JPA, Jackson, Kafka, HTTP. Spring wiring — только `processing/infrastructure/config/ProcessingConfig`.

Шестичастный чеклист:

1. **Use cases / inbound ports**
   - Существующий `ProcessRawEventBatchUseCase` без смены сигнатуры.
   - Новый `PerformEventActionUseCase` в `processing.application.port.in`: `EventActionOutcome perform(EventActionCommand command)`.
   - `EventActionCommand`: `eventId`, `action`, `actorUserId`, `comment`, `assignedUserId`, `silenceMinutes`. Без Spring/`Authentication`.
   - Реализация `PerformEventActionService` в `application.service` (без `@Service` / `@Transactional`).
   - Домен `Event`: поля `acknowledgedAt`, `acknowledgedByUserId`, `silencedUntil`, `silencedByUserId`; методы `acknowledge`, `assignTo`, `silenceUntil`, `take`, `close`, `isSilenced(Instant now)`. Dedup `registerRepeat` не сбрасывает silence.

2. **Inbound adapters**
   - `EventController` остаётся в `processing.api` (исключение D7 / ADR-001: transport rule по-прежнему только `ingestion`, не `processing.api`). Контроллер — inbound adapter: JWT `Authentication` → `EventActionCommand`, вызов порта, маппинг domain → `EventActionResult` через `EventQueryService.toDto`.
   - **Не** переносим контроллер в `adapter/in/web` в этом change (widening ArchUnit — отдельное решение).
   - `EventActionService` удаляется после переноса take/close/comment на use case (один switch, без двух оркестраторов).
   - Query/patch остров **намеренно не** мигрируем: `EventQueryService`, `EventUpdateService` остаются в `processing.api` / `processing.service`. `includeSilenced` добавляется в `EventQueryService.buildSpec` + параметр контроллера. Это **зафиксированное отклонение** того же класса, что D7: read-модель консоли не входит в option A.

3. **Outbound ports**
   - `EventStorePort` — reuse `findById` / `save`; mapper прокидывает новые поля. Новых методов списка не добавлять (список остаётся в `EventQueryService`).
   - Новый `EventActionLogPort.append(ActionLogEntry)` — без Jackson в application; metadata comment — поле доменной записи, JSON пишет adapter.
   - Новый `UserDirectoryPort`: `Optional<UserRef> findById(UUID)` (`id`, `fullName`, `active`). Assign: отсутствует → 404; `active=false` → 400.

4. **Outbound adapter implementations**
   - `EventPersistenceAdapter` + `EventJpaMapper` + колонки `EventJpaEntity`.
   - `EventActionLogPersistenceAdapter` над `EventActionLogJpaRepository` (`action` VARCHAR(64) — миграция журнала не нужна).
   - `UserDirectoryAdapter` в `processing.adapter.out.identity` над существующим `identity.persistence.UserRepository` (не HTTP в admin).

5. **Infrastructure wiring**
   - Бины use case, log/user ports в `ProcessingConfig`.
   - Транзакция: **не** `@Transactional` на application service. Обёртка в `ProcessingConfig` (`TransactionTemplate` / декоратор), чтобы save события + insert журнала атомарны. Отклонение: нет. N/A пунктов нет — все шесть применимы.

6. **Spring-free use-case tests**
   - `PerformEventActionServiceTest` с fake `EventStorePort`, `EventActionLogPort`, `UserDirectoryPort`, фиксированный `Clock`. Без `@SpringBootTest`.
   - `ProcessRawEventBatchServiceTest`: сценарии suppress notify/push при `silencedUntil > now` и восстановление после истечения; `repeatCount` растёт. `NotificationPort`/`PushNotificationPort` — fakes; `markRun` для сматчившихся notify/push rules **всё равно** вызывается (правило сработало, доставка подавлена).

Альтернатива B (точечно расширить `EventActionService`). Отклонена — locked option A, нет hard blocker.

Альтернатива: перенести `EventController` в `adapter/in`. Отклонена в MVP — отдельный ArchUnit widening.

### D5. Silence vs processing pipeline

В `ProcessRawEventBatchService.processRawEvent` после `mergeOrCreate` / `save`: если `saved.isSilenced(clock.instant())`, не вызывать `notifications.notify` и `pushNotifications.createPush`. Dedup, threshold, correlation, `markProcessed` без изменений. Canvas rules не трогаем.

Альтернатива: status `deferred`/`maintenance` как silence. Отклонена (locked: колонка `silenced_until`, chk_status не расширять).

### D6. Liquibase `014-event-duty-actions.sql`

Файл `backend/fm-module/src/main/resources/db/changelog/changes/014-event-duty-actions.sql`, include в `db.changelog-master.yaml` после `013-product-health.sql`.

```sql
ALTER TABLE events ADD COLUMN acknowledged_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN acknowledged_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE events ADD COLUMN silenced_until TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN silenced_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_events_silenced_until ON events (silenced_until) WHERE silenced_until IS NOT NULL;
```

`chk_events_status` не менять. `event_action_logs.action` уже VARCHAR(64).

### D7. REST / OpenAPI / DTO

`docs/fm-module/api.yaml`:

- `EventActionType`: добавить `ack`, `assign`, `silence` (оставить `defer`, `maintenance`).
- `EventActionRequest`: `assignedUserId` — обязателен для `assign` (не override для take); `silenceMinutes` для `silence`.
- `Event`: `acknowledgedAt`, `acknowledgedByUserId`, `silencedUntil`, `silencedByUserId`.
- `GET /api/v1/events`: query `includeSilenced` boolean default false.

Java: расширить `EventActionRequest`, `EventDto`, `EventController.listEvents`, `EventQueryService.toDto` (имена assignee как сейчас через `UserRepository`).

### D8. Frontend SPA

Маршруты без смены. Кнопки **Подтвердить**, **Комментарий**, **Назначить**, **Скрыть** (пресеты 15/30/60 мин) рядом с take/close:

- `event-card-page.component.ts` — полноэкранная карточка.
- `console-page.component.ts` — `.detail-panel` выбранной строки (сейчас title/description). Без bulk, без context menu.

`FmApiService.performEventAction` — body `{ action, comment?, assignedUserId?, silenceMinutes? }`. `listUsers({ active: true })` → `GET /admin/users?active=true`. Модели: `EventActionType` + поля Event.

Комментарий: простой prompt/модалка страницы (существующий overlay-паттерн), пустой текст не слать. Assign: select активных пользователей (исключить можно текущего, но API это не запрещает). Silence: три кнопки/меню пресетов, не свободный ввод в MVP.

Журнал: `formatAction` — человекочитаемые «Подтверждено» / «Комментарий» / «Назначено» / «Скрыто». Карточка: индикация кто/когда ack и «скрыто до silencedUntil».

Опционально тонкий шаблон бара в `frontend/src/app/pages/console/` при дублировании — не новый top-level модуль. `prototype/` не трогать.

### Module split

| Модуль | Что меняется |
|---|---|
| `backend/fm-module` | domain Event; `PerformEventActionUseCase`; ports log/users; `EventController`/`EventQueryService`/`EventDto`; batch silence-check; Liquibase 014; `EventJpaEntity`/mapper; `ProcessingConfig`; тесты |
| `frontend/` | карточка + панель консоли; API client/models; Vitest; Playwright |
| `docs/` | `api.yaml`; `pages-spec.md` (панель действий) |
| `backend/adapter` | нет |
| `backend/zabbix-simulator` | нет |
| `prototype/` | нет |

### Integration points

```text
EventCardPage / ConsolePage
  → FmApiService.performEventAction / listUsers / getEvent / listEvents
  → POST /api/v1/events/{id}/actions | GET /admin/users?active=true | GET /events
  → EventController
  → PerformEventActionUseCase (actions)
  → EventStorePort + EventActionLogPort + UserDirectoryPort

adapter → /api/v1/ingest → ProcessRawEventBatchUseCase
  → merge → if Event.isSilenced(now) skip NotificationPort / PushNotificationPort
```

### Liquibase / REST

См. D6–D7. Compose не меняется: схема поднимется Liquibase при старте fm-module.

### Spec → test mapping

| Capability / scenario | Test task |
|---|---|
| event-duty-ack: ack keeps status, writes columns+log, stays in list | 1.1 Spring-free use case; 2.2 `EventControllerTest` |
| event-duty-ack: repeat ack; 409 closed/archived | 1.1; 2.2 |
| event-duty-comment-ui: UI comment POST; empty 400 | 4.x Vitest; 2.2 comment 200/400; e2e 6.x |
| event-duty-assign: assignee only; take still in_progress | 1.1; 2.2 |
| event-duty-assign: missing/unknown/inactive user | 1.1; 2.2 |
| event-duty-silence: silencedUntil; hidden from list; GET id visible | 1.1; 2.3 list filter; 2.2 action |
| event-duty-silence: suppress notify/push; resume after expiry | 1.2 `ProcessRawEventBatchServiceTest` |
| event-duty-silence: 409 closed; take/close on silenced allowed | 1.1; 2.2 |
| console-duty-actions: card + bottom panel buttons | 4.x Vitest; 6.x Playwright |
| architecture-hexagonal-processing: second inbound port, Spring-free | 1.1; 1.3 ArchUnit still green (не widening transport) |
| rules-notify-block: skip ports when silenced | 1.2 |
| Regression take/close | existing `EventControllerTest` + 2.2 |

## Risks / Trade-offs

- [D7 контроллер не в `adapter/in`] → явно в design и delta `architecture-hexagonal-processing`; ArchUnit transport не расширять.
- [EventQueryService остаётся JPA-островом] → option A не включает list/get; фильтр silence — точечный spec в `buildSpec`.
- [Два пути assignee: PATCH vs assign] → UI duty только через actions; PATCH не удаляем.
- [Silence без job] → список фильтрует `silenced_until > now()`; карточка может показывать истёкший silence как неактивный.
- [Notify suppress vs last_run_at] → `markRun` оставляем, порты доставки не зовём — правило «сработало», шум не уходит.
- [OpenAPI defer/maintenance] → не реализовывать, чтобы не расползтись.
- [listUsers светит email/login дежурному] → locked: тот же admin list с `active=true`.
- [Дублирование кнопок карточка/консоль] → допустим shared bar в том же каталоге console.

## Migration Plan

1. Выкатить fm-module (Liquibase 014 + API) вместе с SPA. Старый клиент без новых кнопок совместим: take/close/comment без новых полей.
2. Rollback: откат SPA; откат сервиса откатит схему только если rollback Liquibase предусмотрен операцией (changeset additive nullable columns — безопасен оставить).
3. Feature-flag не нужен.

## Open Questions

Нет блокирующих. Scope locked: пресеты 15/30/60; ack без self-assign; assign без in_progress; directory admin users; hide только `GET /events`; UI карточка + нижняя панель.

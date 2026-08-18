# Discovery Scope — event-duty-actions

- Change: `event-duty-actions`
- Branch: `feature/FM-10`
- Ключ: `FM-10` (беклог, **без** URL Jira)
- Статус: **scope утверждён** пользователем («утверждаю») со locked defaults §9 (пресеты silence 15/30/60; ack без self-assign; assign без `in_progress`; directory `GET /api/v1/admin/users?active=true`; hide только `GET /events`; UI карточка + нижняя панель). Не переоткрывать §9.
- Q&A оркестратора: пользователь выбрал FM-10, затем утвердил все дефолты. Ниже — исходный discovery; реализация следует proposal/design/specs, не этому черновику.

## 1. Problem statement

Консоль — рабочее место NOC, но дежурный умеет только **принять в работу себе** (`take` → `in_progress` + `assigned_user_id` = текущий пользователь) и **закрыть** (`close`). Нельзя подтвердить аварию, не беря её в работу; в UI нет комментария (хотя API `comment` уже есть); нельзя назначить коллеге с записью в журнал; нельзя временно спрятать шум, не сбрасывая PROBLEM.

## 2. Why / What Changes

**Зачем.** Те же действия, что в оперцентре Monq: ack / comment / assign / silence, без эскалаций и ITSM.

**Что меняем (предлагаемый объём):**

| Действие | Поведение (дефолт) |
|---|---|
| **ack** | Подтвердить. Статус **не** меняется (`new` остаётся `new`). Пишем `acknowledged_at` / `acknowledged_by_user_id` + запись в `event_action_logs`. Событие остаётся активным (PROBLEM). |
| **comment** | Кнопка в UI на уже существующий `POST .../actions` с `action=comment`. Текст обязателен. Статус/assignee не трогаем. |
| **assign** | Назначить **другого** активного пользователя. Меняем только `assigned_user_id` (статус не меняем, это не `take`). Журнал обязателен. `take` по-прежнему назначает на себя и ставит `in_progress`. |
| **silence** | Скрыть на N минут: не показывать в списке активных, не слать notify/push при повторах, **не** закрывать и **не** менять status/severity. Хранение: `silenced_until` (не новый status). По истечении событие снова в активных; PROBLEM живой, дедуп продолжает мержить. |
| **take / close** | Не ломать текущие сценарии `EventControllerTest`. |

Единая точка API: расширить существующий `POST /api/v1/events/{id}/actions` (не плодить отдельные ресурсы).

## 3. Modules

Для `state.modules` (пишет оркестратор, не этот агент):

- `backend/fm-module`
- `frontend/`

**Не затрагиваются:** `backend/adapter`, `backend/zabbix-simulator`, `prototype/`.

## 4. Flags

| Флаг | Да/нет | Что именно |
|---|---|---|
| **Liquibase/SQL** | **Да** | Новый changeset после `013-product-health.sql` → `014-event-duty-actions.sql` в `backend/fm-module/src/main/resources/db/changelog/changes/` + include в `db.changelog-master.yaml`. Колонки на `events`: `acknowledged_at TIMESTAMPTZ`, `acknowledged_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL`, `silenced_until TIMESTAMPTZ`, опционально `silenced_by_user_id`. Индекс под фильтр списка (например partial `WHERE silenced_until IS NOT NULL`). **Не** расширять `chk_events_status` — silence не статус. `event_action_logs.action` уже `VARCHAR(64)` — новые значения `ack`/`assign`/`silence` без миграции журнала. |
| **REST** | **Совместимо** (additive) | Тот же `POST /api/v1/events/{id}/actions`. Новые `action`: `ack`, `assign`, `silence`. Расширить body: `assignedUserId` (уже есть в OpenAPI, **нет** в Java `EventActionRequest`), `silenceMinutes` (integer). Существующие `take`/`close`/`comment` без breaking change. `GET /api/v1/events` — новый query `includeSilenced` (default `false`): по умолчанию скрывать `silenced_until > now()`. `GET /{id}` всегда возвращает событие, в т.ч. silenced. |
| **OpenAPI** | **Да** | `docs/fm-module/api.yaml`: `EventActionType` сейчас `[take, close, comment, defer, maintenance]` — **добавить** `ack`, `assign`, `silence`; `defer`/`maintenance` **не реализовывать** (дырка контракта vs код). Схема `Event`: `acknowledgedAt`, `acknowledgedByUserId`, `silencedUntil`. `EventActionRequest`: `assignedUserId` уже описан как override для `take` — уточнить: для `assign` обязателен; для silence — `silenceMinutes`. |
| **Docker Compose** | **Нет** | Схема поднимется Liquibase при старте `fm-module`. |
| **prototype-only** | **Нет** | Production SPA `frontend/`. Прототип не трогаем. |

## 5. Non-goals

Из беклога:

- матрицы эскалации, смены/on-call;
- downtime-календарь как модуль (`/downtime` в `docs/pages-spec.md` — post-MVP);
- согласование, ITSM (поле `itsmIncidentNumber` / PATCH не расширяем под этот change);
- массовые операции на тысячи строк;
- WebSocket (polling консоли остаётся).

Дополнительно из кода (чтобы не расползтись):

- **не** реализовывать OpenAPI-заглушки `defer` / `maintenance` и не использовать status `deferred`/`maintenance` как silence;
- **не** трогать `assigned_team_id` из `docs/requirements.md` (колонки нет);
- **не** чинить исполнение query карт (`event_maps.query = 'status != closed'`): `EventController.listEvents` **игнорирует** `mapId`, который шлёт UI — это отдельный долг, не FM-10;
- **не** менять health/dashboard counters (silence = шум в консоли, PROBLEM для здоровья продукта остаётся);
- **не** adapter ingest, zabbix-simulator, Kafka, правило-canvas (кроме подавления исходящих notify/push на уже сохранённом событии);
- **не** отдельный top-level модуль.

## 6. Acceptance criteria (проверяемые)

Каждый критерий из FM-10 → capability/scenario для specs.

### ACK (подтверждение)

- **GIVEN** активное событие (`new` или `in_progress`)
- **WHEN** оператор вызывает `POST /api/v1/events/{id}/actions` с `action=ack`
- **THEN** `status` не меняется, `closedAt` пустой, заполнены `acknowledgedAt` / `acknowledgedByUserId`, в журнале запись `ack`
- **AND** событие остаётся в активном списке (silence не ставится)
- **AND** повторный ack разрешён (обновляет timestamp)
- **AND** ack на `closed`/`archived` → 409

### COMMENT (UI на существующий API)

- **GIVEN** API `comment` уже есть в `EventActionService` (без смены статуса, comment обязателен)
- **WHEN** оператор нажимает «Комментарий» на карточке / в консоли и вводит текст
- **THEN** тот же `POST .../actions` `{ action: "comment", comment }` → 200, строка в журнале вкладки «Журнал»
- **AND** пустой comment → 400 (как сейчас)
- **AND** take/close по-прежнему работают

### ASSIGN (на коллегу)

- **GIVEN** другой активный пользователь в системе
- **WHEN** оператор выбирает его и делает `action=assign` + `assignedUserId`
- **THEN** `assignedUserId`/`assignedUserName` обновлены, **status не** становится `in_progress` только из-за assign, в журнале `assign`
- **AND** неизвестный / неактивный пользователь → 400/404
- **AND** без `assignedUserId` → 400
- **AND** существующий `take` по-прежнему ставит assignee = текущий пользователь и `status=in_progress`, `takenAt=now`

### SILENCE (N минут)

- **GIVEN** активное событие
- **WHEN** оператор делает `action=silence` с `silenceMinutes=N` (N > 0)
- **THEN** `status`/`severity` не меняются, PROBLEM не закрыт, `silencedUntil = now+N`
- **AND** `GET /api/v1/events` без `includeSilenced=true` **не** возвращает это событие, пока `silencedUntil > now`
- **AND** `GET /api/v1/events/{id}` возвращает событие (карточка по прямой ссылке жива)
- **AND** повтор сырого события (dedup merge) **увеличивает** `repeatCount` / `lastRepeatAt`, но **не** вызывает `NotificationPort.notify` и **не** создаёт push
- **AND** после истечения `silencedUntil` событие снова в списке, notify/push на последующих повторах снова идут
- **AND** silence на closed/archived → 409
- **AND** take/close на silenced событии разрешены (карточка); close по-прежнему закрывает

### Консоль + карточка

- Карточка `/console/:eventId`: кнопки **Подтвердить**, **Комментарий**, **Назначить**, **Скрыть на N мин** рядом с существующими **Принять в работу** / **Закрыть** (`event-card-page.component.ts`).
- Консоль `/console`: те же действия с выбранной строки (нижняя панель; контекстное меню по `pages-spec.md` — допустимая реализация). Без bulk.
- Журнал показывает человекочитаемые подписи ack/comment/assign/silence.
- Индикация на карточке: подтверждено (кто/когда), скрыто до `silencedUntil`.

### Регрессия

- `take` / `close` контракт `EventControllerTest` зелёный.
- Нет изменений compose / adapter / simulator.

## 7. Suggested change name

Оставить **`event-duty-actions`**.

## 8. Draft bullets for proposal.md

### Why

- Дежурный в консоли не может подтвердить, прокомментировать, переназначить или временно спрятать шум — только take себе и close.
- API comment уже есть, UI нет; OpenAPI обещает assign override и defer/maintenance, код этого не делает.

### What Changes

- Расширить `POST /api/v1/events/{id}/actions`: `ack`, `assign`, `silence` + UI на карточке и в консоли.
- Liquibase: ack/silence колонки на `events`; список активных исключает текущий silence.
- Processing: в `ProcessRawEventBatchService` не слать notify/push, если `silencedUntil` в будущем; дедуп не отключать.
- OpenAPI `docs/fm-module/api.yaml` синхронизировать с реальными action.

### Capabilities (для delta specs)

1. `event-duty-ack` — подтверждение без смены статуса
2. `event-duty-comment-ui` — кнопка comment на существующий API
3. `event-duty-assign` — назначение коллеге + журнал
4. `event-duty-silence` — временное скрытие + suppress notify/push
5. `console-duty-actions` — действия в SPA (карточка + выбранная строка)

### Impact

- `backend/fm-module`: `EventActionService` / (флаг архитектору: вынести use case), `EventJpaEntity`, domain `Event`, `EventDto`/`EventActionRequest`, `EventQueryService.listEvents`, `ProcessRawEventBatchService`, Liquibase `014-…`, тесты `EventControllerTest` + Spring-free use case.
- `frontend/`: `event-card-page`, `console-page` (панель выбранного), `fm-api.service`, `api.models` (`EventActionType`).
- Docs: `docs/fm-module/api.yaml`; по желанию строка в `docs/pages-spec.md` (панель действий уже перечисляет «Комментарий | Подтвердить»).
- Tests: `cd backend/fm-module && mvn test`; `cd frontend && npm test`; Playwright e2e на карточку (сейчас e2e консоли нет).

### ADR-001 (флаг для design, не design)

`processing` уже в пилоте hexagonal (`docs/adr/ADR-001-hexagonal-architecture.md`). Сейчас:

- Домен события: `ru.wisla.fm.processing.domain.Event` (нет ack/silence полей).
- Обработка + notify: hexagonal `ProcessRawEventBatchService` → `NotificationPort` / `PushNotificationPort`.
- Действия консоли — **ещё слоёный** остров: `processing.service.EventActionService` (`@Service`, JPA, `Authentication`) + `processing.api.EventController` (исключение D7: контроллер не в `adapter/in`).
- ArchUnit: `HexagonalArchitectureTest` — processing в `IN_SCOPE`, но transport rules **не** покрывают `processing.api`.

Design должен выбрать: (A) новый inbound port `PerformEventActionUseCase` в `processing.application` с outbound store/log/users; или (B) точечно расширить `EventActionService` и явно зафиксировать исключение. Рекомендация аналитика: **A** для новых action + silence-check в уже hexagonal processing. Не проектировать пакеты в этом документе.

## 9. Follow-up questions (только критичные)

Дефолты ниже — оркестратор спрашивает «ок / поправить», а не invent from scratch.

1. **Silence: длительности.** Дефолт: пресеты **15 / 30 / 60** минут в UI; API принимает любой `silenceMinutes > 0`; авто-снятие по `silencedUntil`; ручное снятие = `silenceMinutes` не нужен — отдельный `action=unsilence` **не** делаем в MVP (можно снова silence с маленьким N или close). **TODO: orchestrator must ask user**, если нужны 4 ч / произвольное число в UI / кнопка «снять сейчас».
2. **Ack-модель.** Дефолт: колонки `acknowledged_at` + `acknowledged_by_user_id`, статус не меняем, un-ack нет. **TODO: orchestrator must ask user**, если ack должен ещё и назначать на себя (это уже `take`).
3. **Assign vs take.** Дефолт: assign **не** меняет status; take по-прежнему self + `in_progress`. **TODO: orchestrator must ask user**, если assign коллеге должен автоматически переводить в `in_progress`.
4. **Справочник пользователей.** `GET /api/v1/admin/users` **уже доступен любому authenticated** (`AdminService.listUsers` без `requireAdmin`; admin нужен на create/patch/delete). Дефолт: UI assign ходит в `GET /api/v1/admin/users?active=true` через существующий `FmApiService.listUsers()`. Отдельный `/users/directory` не делаем. **TODO: orchestrator must ask user**, только если нельзя светить admin-список дежурному (email/login) — тогда узкий lookup `id+fullName`.
5. **Где прячем silence.** Дефолт: `GET /api/v1/events` скрывает текущий silence; карточка по id видна; **health не** исключает silenced из active count. **TODO: orchestrator must ask user**, если нужен отдельный список «скрытые» или скрытие со счётчиков дашборда/health.
6. **Консоль.** Дефолт: кнопки на полноэкранной карточке **и** на нижней панели выбранной строки (сейчас там только title/description). Без контекстного меню и без bulk. **TODO: orchestrator must ask user**, если достаточно только карточки.

Не спрашивать: WebSocket, adapter, compose, prototype, defer/maintenance, эскалации.

## 10. Existing APIs/UI vs new work

### Уже есть — переиспользовать

| Что | Где |
|---|---|
| `POST /api/v1/events/{id}/actions` | `EventController.postAction` |
| Actions `take`, `close`, `comment` | `EventActionService.performAction` |
| Журнал `event_action_logs` | `007-action-logs.sql`, вкладка «Журнал» карточки |
| `assigned_user_id` + `assignedUserName` в DTO | `EventJpaEntity`, `EventQueryService.toDto` |
| PATCH assignee без журнала | `EventUpdateService` + `EventPatch.assignedUserId` — **для duty-assign не использовать как основной путь** (нет audit); оставить как есть |
| Список пользователей | `GET /api/v1/admin/users`, UI `listUsers()` |
| Notify/push после обработки | `ProcessRawEventBatchService` строки notify/push intents → `NotifyAdapter` / `PushNotificationAdapter` |
| UI take/close | `frontend/.../event-card-page.component.ts` кнопки «Принять в работу» / «Закрыть» |
| Клиент действий | `FmApiService.performEventAction` — расширить body |
| Тесты take/close | `EventControllerTest` |
| Спеки консоли (сорт, last-repeat) | `openspec/specs/console-column-sort`, `console-last-repeat-column` |
| Notify/push контракт правил | `openspec/specs/rules-notify-block` — не менять canvas; только suppress на silenced event |

### Нет / дырки (новая работа)

| Что | Сейчас |
|---|---|
| `ack` / `assign` / `silence` в switch | `default → Unsupported action` |
| `EventActionRequest` | только `action`, `comment` — нет `assignedUserId` (в OpenAPI для take override — мёртвое поле) |
| Колонки ack/silence | нет в `006-processing.sql` / `EventJpaEntity` / domain `Event` / `EventDto` |
| UI comment/ack/assign/silence | только take+close; консоль: таблица + пустая нижняя панель, **нет** row actions |
| Фильтр silenced в list | `EventQueryService.buildSpec` — status/severity/sourceId/ciId |
| Suppress notify | intents всегда исполняются после `mergeOrCreate`, в т.ч. на повтор |
| Тест `comment` | нет (ни unit `EventActionService`, ни controller) |
| OpenAPI vs код | enum содержит `defer`,`maintenance`; код их не знает |

### Карты / «активные»

Системная карта `Активные` (`010-console.sql`): query `status != closed`. Backend `listEvents` **не применяет** `mapId`. Для FM-10 «не показывать в активных» = **серверный** exclude по `silenced_until`, не rewrite карт.

## Question-bank coverage (discovery)

| # | Ответ |
|---|---|
| 1 ключ | FM-10, беклог, без Jira URL |
| 2 тип | новая функция |
| 3 дедлайн | не задан |
| 4 AC | см. §6; уточнения §9 |
| 5 модули | `backend/fm-module`, `frontend/` |
| 6 compose | нет |
| 7 Angular | консоль + карточка |
| 8 prototype-only | нет |
| 9 Liquibase | да, `014-event-duty-actions.sql` |
| 10 REST | additive; OpenAPI да |
| 11 adapter ingest | нет |
| 12 rule canvas | нет (только suppress исходящих notify/push) |
| 13 non-goals | §5 |
| 14 min diff | да, плюс флаг hexagonal для action use case |
| 15 связанные specs | console-*, rules-notify-block, architecture-hexagonal-processing |
| 16 тесты | `mvn test` fm-module; `npm test`; Playwright e2e карточки |
| 17 demo-script | по желанию шаг «дежурный: ack/comment/assign/silence» — не блокер scope |

## Mapping Jira/backlog AC → capability

| AC из FM-10 | Capability |
|---|---|
| ack, событие остаётся активным | `event-duty-ack` |
| comment, кнопка на существующий API | `event-duty-comment-ui` |
| assign на другого пользователя | `event-duty-assign` |
| silence N мин: не в активных, не notify, не сброс PROBLEM | `event-duty-silence` |
| на карточке и из консоли | `console-duty-actions` |
| take/close не ломать | регрессия в каждом spec + существующие тесты |

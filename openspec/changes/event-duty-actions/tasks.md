## 1. backend/fm-module — Spring-free tests (red)

- [x] 1.1 Add `PerformEventActionServiceTest` (no Spring context) with fakes of `EventStorePort`, `EventActionLogPort`, `UserDirectoryPort` and a fixed `Clock`. Cover **Ack keeps status and writes audit columns**, **Repeat ack updates timestamp**, **Ack on closed or archived is rejected** (`event-duty-ack`) — red
- [x] 1.2 Same spec: **Assign colleague keeps status**, **Take still self-assigns and sets in_progress**, **Assign without assignedUserId is rejected**, **Unknown assignee is rejected**, **Inactive assignee is rejected** (`event-duty-assign`); **Blank comment is rejected** (`event-duty-comment-ui`) — red
- [x] 1.3 Same spec: **Silence sets silencedUntil and keeps PROBLEM**, **SilenceMinutes must be positive**, **Silence on closed or archived is rejected**, **Take and close remain allowed on a silenced event** (`event-duty-silence`) — red
- [x] 1.4 Extend `ProcessRawEventBatchServiceTest`: **Repeat while silenced suppresses notify and push**, **Repeat after silence expires notifies again** (`event-duty-silence`); **Notify skipped while event is silenced** / **Push skipped while event is silenced** (`rules-notify-block`). Dedup still increments `repeatCount` / `lastRepeatAt`; `markRun` still records matching rule ids — red

## 2. backend/fm-module — Liquibase

- [x] 2.1 Add `backend/fm-module/src/main/resources/db/changelog/changes/014-event-duty-actions.sql`: `acknowledged_at`, `acknowledged_by_user_id` (FK `users` ON DELETE SET NULL), `silenced_until`, `silenced_by_user_id` (same FK); partial index on `silenced_until` WHERE NOT NULL. Do not change `chk_events_status`. Include after `013-product-health.sql` in `db.changelog-master.yaml`

## 3. backend/fm-module — hexagonal use case (green for 1.x)

- [x] 3.1 Add domain fields/methods on `processing.domain.Event`: `acknowledgedAt`, `acknowledgedByUserId`, `silencedUntil`, `silencedByUserId`; `acknowledge`, `assignTo`, `silenceUntil`, `take`, `close`, `isSilenced(Instant)` — green for domain assertions in 1.1–1.3
- [x] 3.2 Declare inbound `PerformEventActionUseCase` and outbound `EventActionLogPort`, `UserDirectoryPort`; implement Spring-free `PerformEventActionService` (no `@Service` / `@Transactional` / Jackson). Map miss event/user to 404/400 via exceptions documented in design.md — green for 1.1–1.3
- [x] 3.3 Implement `EventActionLogPersistenceAdapter` and `UserDirectoryAdapter` (`processing.adapter.out.identity` over existing `UserRepository`); map new columns in `EventJpaEntity` / `EventJpaMapper` / `EventPersistenceAdapter.save|findById`
- [x] 3.4 Wire `PerformEventActionUseCase` in `ProcessingConfig` with `TransactionTemplate` (or equivalent decorator) so event save + log insert are atomic; inject `Clock.systemUTC()` like the batch use case
- [x] 3.5 In `ProcessRawEventBatchService`, skip `NotificationPort.notify` and `PushNotificationPort.createPush` when `saved.isSilenced(clock.instant())`; keep dedup/threshold/correlation/`markRun` — green for 1.4
- [x] 3.6 Point `EventController.postAction` at `PerformEventActionUseCase` (controller stays in `processing.api`, D7); delete `EventActionService` after take/close/comment live on the use case. Do not move the controller to `adapter/in` and do not widen ArchUnit transport to `processing.api`

## 4. backend/fm-module — REST / query tests then impl

- [x] 4.1 Extend `EventControllerTest`: ack 200 + columns + journal, repeat ack, ack 409 on closed/archived; comment 200/400; assign 200 without `in_progress`, 400 missing id, 404 unknown, 400 inactive; take still `in_progress`; silence 200, 400 bad minutes, 409 closed; take/close on silenced 200 (`event-duty-ack` / `assign` / `comment-ui` / `silence`). Existing take/close cases stay green — red then green with 4.3
- [x] 4.2 Extend list/detail tests: `GET /api/v1/events` hides `silencedUntil > now()` by default; `includeSilenced=true` returns them; `GET /{id}` returns silenced; expired silence is listed (`event-duty-silence` list/detail) — red then green with 4.3
- [x] 4.3 Expand `EventActionRequest` (`assignedUserId`, `silenceMinutes`), `EventDto` (ack/silence fields), `EventController.listEvents(includeSilenced default false)`, `EventQueryService.buildSpec` / `toDto`. Health/dashboard endpoints unchanged
- [x] 4.4 Run `cd backend/fm-module && mvn test` — green; `HexagonalArchitectureTest` still passes (processing domain/application Spring-free; no transport-rule widening)

## 5. OpenAPI and pages-spec

- [x] 5.1 Update `docs/fm-module/api.yaml`: `EventActionType` add `ack`, `assign`, `silence` (keep `defer`/`maintenance` unimplemented); `EventActionRequest` `assignedUserId` for assign, `silenceMinutes` for silence; `Event` ack/silence fields; `GET /api/v1/events` query `includeSilenced` boolean default false
- [x] 5.2 Update `docs/pages-spec.md` console/card action bar: Подтвердить, Комментарий, Назначить, Скрыть 15/30/60 on card and selected-row bottom panel; no bulk; no context menu

## 6. frontend/ — Vitest (red) then UI (green)

- [x] 6.1 Add/extend Vitest for `FmApiService`: `performEventAction` posts `{ action, comment?, assignedUserId?, silenceMinutes? }`; `listUsers({ active: true })` calls `GET /admin/users?active=true`. Extend `EventActionType` and `Event` fields in `api.models.ts` — red then green with 6.4
- [x] 6.2 Add Vitest for `EventCardPageComponent`: **Card shows duty action buttons**, **Journal labels are human-readable**, **Card indicates ack and silence**, **Operator comments from the card**, **Assign picker uses active admin users** (`console-duty-actions` / `event-duty-comment-ui`). Mock `FmApiService`. Take/close still callable — red
- [x] 6.3 Add Vitest for `ConsolePageComponent` selected `.detail-panel`: **Console bottom panel acts on the selected row**, **Operator comments from the console bottom panel**; no bulk toolbar; no context menu — red
- [x] 6.4 Implement card + console bottom panel controls (ack, comment prompt/modal, assign select, silence presets 15/30/60) next to take/close; `formatAction` labels; ack/silence indicators. Optional shared bar under `frontend/src/app/pages/console/` only — not a new top-level module. Do not change routes or `prototype/`
- [x] 6.5 Run `cd frontend && npm test` — green for 6.1–6.3

## 7. frontend/ — Playwright e2e (backend up)

- [x] 7.1 Add Playwright spec covering card `/console/:eventId`: visible duty buttons; ack then reload shows acknowledgement; comment appears in journal; assign changes assignee without forcing in_progress label alone; silence 15/30/60 posts `silenceMinutes` — maps **Card shows duty action buttons**, comment, assign, silence UI
- [x] 7.2 Same or sibling spec on `/console`: select a row, run ack/comment/assign/silence from the bottom panel (**Console bottom panel acts on the selected row**); assert list polling omits a silenced event (default `includeSilenced`)
- [x] 7.3 Run `cd frontend && npm run test:e2e` against running backend (`http://localhost:8080`) — green

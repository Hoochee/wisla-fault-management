# Discovery Scope — hexagonal-refactor

- Change: `hexagonal-refactor`
- Branch: `feature/WISLA-refactor`
- Jira: нет (инициатива пользователя, рефакторинг)
- Governing ADR: `docs/adr/ADR-001-hexagonal-architecture.md` (Accepted)
- Статус: ожидает утверждения scope (`approvals.scope = false`)

## Problem statement

Оба backend-сервиса (`backend/adapter`, `backend/fm-module`) реализованы как классический слоёный Spring/JPA: `@Entity`-классы лежат прямо в пакетах `domain/`, use-case-сервисы напрямую внедряют Spring Data репозитории, `ObjectMapper`, `PasswordEncoder` и `Authentication`, а бизнес-решения (фильтрация, нормализация severity, dedup/threshold/correlation, обход графа правил) невозможно протестировать без запуска Spring-контекста и H2. ADR-001 уже зафиксировал целевую гексагональную структуру, но описан как «process-only» и не был применён к существующему коду. Данный change — первый предметный пилот ADR-001: перевести ingest-срез и ядро движка обработки на порты/адаптеры без изменения внешнего поведения.

## Scope (решение пользователя, binding)

- **Phase 1** — ingest-вертикаль: `backend/adapter` (webhook, pre-filter, буфер + retry, поиск конфигурации источника) + `backend/fm-module` контекст `ingestion`.
- **Phase 2** — ядро обработки: `backend/fm-module` контекст `processing` (включая canvas-runtime) с исходящими портами в `cmdb` / `rules` / `notifications`.
- **JPA = полный split**: чистые доменные модели; JPA-классы переносятся/переименовываются в `adapter/out/persistence` как `*JpaEntity`; мапперы — рукописные (MapStruct отложен).
- **Транзакции = как есть**: `ingest → processBatch` остаётся одной транзакцией, поведение при ошибке не меняется.
- **ArchUnit = включаем сейчас**, вместе с Phase 1.

## Проверенные факты по коду (базовая линия)

`backend/adapter` (`com.wisla.fm.adapter`):

| Класс | Проблема с точки зрения ADR-001 |
|---|---|
| `service/WebhookService` | use case внедряет `ObjectMapper`, `PasswordEncoder`, `AdapterProperties`, работает напрямую с `@Entity SourceConfigSnapshot`/`BufferedMessage` |
| `service/FilterService` | чистая логика, но помечена `@Service` и принимает `Map<String,Object>` |
| `service/IngestPayloadMapper` | доменная нормализация (severity, zabbix `event_value=0`, probe→heartbeat) в `@Service`, зависит от `AdapterProperties` и `Instant.now()` |
| `service/BufferService`, `service/BufferRetryWorker` | `@Scheduled` + `@Transactional` + Spring Data в одном классе; backoff-логика внутри `BufferedMessage.scheduleRetry` |
| `service/SourceConfigService` | lookup + маппинг в web-DTO в одном сервисе |
| `persistence/entity/SourceConfigSnapshot` (`source_config_snapshots`), `persistence/entity/BufferedMessage` (`buffered_messages`) | `@Entity` + `@JdbcTypeCode(SqlTypes.JSON)` для `filter_rules` / `payload` |
| `kafka/RawEventPublisher` | **единственный корректный порт в кодовой базе**; реализация `RawEventKafkaPublisher` |

`backend/fm-module` (`ru.wisla.fm`):

| Класс | Проблема |
|---|---|
| `ingestion/api/IngestService` | `@Transactional` use case внедряет `Authentication`, `ObjectMapper`, `RawEventRepository`, `EventSourceRepository` и напрямую вызывает `EventProcessingService` |
| `ingestion/api/IngestController`, `ingestion/kafka/RawEventKafkaListener` | входные адаптеры, но listener сам читает `EventSourceRepository` |
| `ingestion/domain/RawEventEntity` (`raw_events`) | `@Entity` в `domain` |
| `processing/service/EventProcessingService` | оркестрация + маппинг raw→event + шаблонизация push-сообщения; тянет `cmdb.CiService`, `rules.ProcessingRuleRepository`, `notifications.*` напрямую |
| `processing/service/DedupService`, `ThresholdService`, `CorrelationService` | доменные правила смешаны с производными запросами Spring Data |
| `processing/canvas/RuleCanvasEngine` | `@Service` + `@Transactional`, внедряет `ObjectMapper` и `ProcessingRuleRepository`, хранит несинхронизированный `HashMap` plan-кеш; импортирует `rules.api.RuleCanvasDto` |
| `processing/canvas/RuleCanvasCompiler`, `RuleConditionEvaluator`, `SwitchBranchSelector`, `ProcessingDecision`, `*Config` | фактически чистая логика под `@Component`/`@Service` |
| `processing/domain/EventEntity` (`events`), `EventActionLogEntity` (`event_action_logs`) | `@Entity` в `domain` |
| `notifications/api/NotifyStubService` | обратная зависимость: импортирует `processing.canvas.ProcessingDecision` |
| `rules/api/RuleCanvasValidator` | обратная зависимость: импортирует `processing.canvas.CanvasNodeView/CanvasEdgeView` |

Инфраструктура: ни MapStruct, ни ArchUnit, ни Testcontainers в `pom.xml` нет; тесты — `@SpringBootTest` + H2 (`AbstractFmModuleTest`) и `@EmbeddedKafka`. В обоих `pom.xml` `java.version = 25`, Spring Boot 3.4.5.

Внешние потребители `EventEntity`/`EventRepository` **вне scope**, которых затронет переименование: `dashboard/DashboardService`, `admin/AdminService`, `configuration/SourceService`, `health/ProductHealthService`, `config/DevDataSeeder`, а также console-сервисы внутри `processing`: `api/EventQueryService`, `service/EventActionService`, `service/EventUpdateService`.

## What changes — Phase 1 (ingest-вертикаль)

### 1.1 `backend/adapter` — контекст `ingest`

Целевая структура (root `com.wisla.fm.adapter.ingest`, приложение остаётся `AdapterApplication`):

```text
com.wisla.fm.adapter.ingest/
  domain/                 SourceConfig, FilterRules, FilterCondition, BufferedEvent,
                          IngestPayloadNormalizer, DeliveryOutcome, IngestRejection
  application/port/in/    ReceiveWebhookEventUseCase, DeliverIngestEventUseCase,
                          RetryBufferedEventsUseCase, SyncSourceConfigUseCase
  application/port/out/   SourceConfigLookupPort, SourceConfigStorePort,
                          BufferedEventStorePort, RawEventPublisherPort,
                          ApiKeyVerifierPort, FmModuleSourceConfigPort
  application/service/    ReceiveWebhookEventService, RetryBufferedEventsService,
                          SyncSourceConfigService
  adapter/in/web/         WebhookController, InternalController, HealthController,
                          WebhookPayloadReader, GlobalExceptionHandler, dto/*
  adapter/in/scheduler/   BufferRetryScheduler, SourceConfigSyncScheduler
  adapter/out/persistence/ SourceConfigSnapshotJpaEntity, BufferedMessageJpaEntity,
                          SourceConfigSnapshotJpaRepository, BufferedMessageJpaRepository,
                          SourceConfigPersistenceAdapter, BufferedEventPersistenceAdapter,
                          SourceConfigJpaMapper, BufferedEventJpaMapper
  adapter/out/kafka/      RawEventKafkaPublisher, RawEventEnvelope, RawEventEnvelopeCodec
  adapter/out/http/       FmModuleSourceConfigClient, FmModuleClient
  adapter/out/crypto/     PasswordEncoderApiKeyVerifier
  infrastructure/config/  AppConfig, WebClientConfig, AdapterProperties, KafkaIngestProperties
```

Inbound-порты:

- `ReceiveWebhookEventUseCase.receive(ReceiveWebhookCommand) : DeliveryOutcome` — заменяет `WebhookService.receive(...)`.
- `DeliverIngestEventUseCase.deliver(DeliverCommand) : DeliveryOutcome` — публичный путь, который сегодня использует `ProbeService` через package-private `WebhookService.deliver(...)`.
- `RetryBufferedEventsUseCase.retryDueMessages(Instant)` — заменяет тело `BufferRetryWorker.retryBufferedMessages()`.
- `SyncSourceConfigUseCase.sync()` — заменяет `SourceConfigSyncService.syncFromFmModule()`.

Outbound-порты:

- `SourceConfigLookupPort` — `findBySourceKey(String)`, `findBySourceId(UUID)` → `Optional<SourceConfig>`.
- `SourceConfigStorePort` — `upsert(SourceConfig)` (сохраняет семантику `SourceConfigSnapshot.replace(...)`: `created_at` не перезаписывается).
- `BufferedEventStorePort` — `save(BufferedEvent)`, `findDue(Instant)`, `delete(BufferedEvent)`, `count()`.
- `RawEventPublisherPort` — существующий `RawEventPublisher` (контракт `PublishResult(success, error, retryable)` сохраняется 1:1), перемещается в `application/port/out`.
- `ApiKeyVerifierPort.matches(rawKey, storedHash)` — убирает `PasswordEncoder` из use case.
- `FmModuleSourceConfigPort.fetchSources()` — убирает `RestClient` из use case синхронизации.
- Время: use-case-сервисы получают `java.time.Clock` (JDK, не framework), вместо `Instant.now()` внутри логики.

Адаптеры / переносы классов:

| Сейчас | Станет |
|---|---|
| `web/WebhookController` | `adapter/in/web/WebhookController` + `WebhookPayloadReader` (чтение body, лимит `max-payload-bytes` → 413, парсинг JSON → 400 `invalid_json`) |
| `web/InternalController`, `web/HealthController`, `web/dto/*`, `web/GlobalExceptionHandler` | `adapter/in/web/...` (без изменения путей и DTO) |
| `service/WebhookService` | `application/service/ReceiveWebhookEventService` |
| `service/FilterService` | `domain/FilterRules` + `FilterCondition` (правила становятся поведением доменной модели, без `@Service`) |
| `service/IngestPayloadMapper` | `domain/IngestPayloadNormalizer` (принимает `adapterVersion` и `Clock` параметрами) |
| `service/BufferService` | растворяется в `BufferedEventStorePort` + `BufferedEvent` |
| `service/BufferRetryWorker` | `application/service/RetryBufferedEventsService` + `adapter/in/scheduler/BufferRetryScheduler` (`@Scheduled`, `@Transactional`) |
| `service/SourceConfigService` | `application/service` часть → порт lookup; `toDto(...)` → маппер в `adapter/in/web` |
| `service/SourceConfigSyncService` | `application/service/SyncSourceConfigService` + `adapter/out/http/FmModuleSourceConfigClient` + `adapter/in/scheduler/SourceConfigSyncScheduler` (`ApplicationRunner` остаётся в адаптере) |
| `service/ProbeService`, `service/HealthService` | остаются use-case/сервисами, переводятся на `DeliverIngestEventUseCase` и порты (минимальные механические правки) |
| `persistence/entity/SourceConfigSnapshot` | `adapter/out/persistence/SourceConfigSnapshotJpaEntity` (таблица и колонки без изменений) |
| `persistence/entity/BufferedMessage` | `adapter/out/persistence/BufferedMessageJpaEntity`; backoff (`retry_count`, `2^min(n-1,10)`) переезжает в `domain/BufferedEvent.scheduleRetry(baseSeconds, now)` |
| `kafka/*` | `adapter/out/kafka/*` |
| `config/*` | `infrastructure/config/*` |

Доменные модели и их JPA-двойники (Phase 1):

| Доменная модель | JPA-класс | Таблица |
|---|---|---|
| `SourceConfig` (sourceId, sourceKey, apiKeyHash, endpoint, `FilterRules`, blocked, ttlExpiresAt, createdAt, updatedAt; `isExpired(Clock)`) | `SourceConfigSnapshotJpaEntity` | `source_config_snapshots` |
| `BufferedEvent` (id, sourceId, ingestApiKey, payload, retryCount, nextRetryAt, createdAt, updatedAt; `scheduleRetry`) | `BufferedMessageJpaEntity` | `buffered_messages` |
| `FilterRules` / `FilterCondition` (enabled, drop_if, pass_only; операторы eq/ne/contains/in/gt/lt/exists, dotted-path) | — (jsonb-колонка `filter_rules`, мапится в модель) | — |

### 1.2 `backend/fm-module` — контекст `ingestion`

```text
ru.wisla.fm.ingestion/
  domain/                 RawEvent, RawEventBatch, IngestOutcome, SourceIngestState
  application/port/in/    IngestEventsUseCase, QueryRawEventsUseCase
  application/port/out/   RawEventStorePort, EventSourceStatePort, ProcessRawEventBatchPort
  application/service/    IngestEventsService, RawEventQueryService
  adapter/in/web/         IngestController, RawEventController, IngestRequest, IngestResponse,
                          RawEventDto, RawEventPage
  adapter/in/messaging/   RawEventKafkaListener, RawEventEnvelope
  adapter/out/persistence/ RawEventJpaEntity, RawEventJpaRepository,
                          RawEventPersistenceAdapter, RawEventJpaMapper
  adapter/out/processing/  ProcessRawEventBatchAdapter
  infrastructure/config/
```

- `IngestEventsUseCase.ingest(IngestCommand)` — `IngestCommand(sourceId, heartbeat, events, adapterVersion, receivedAt)`. `Authentication` больше не доходит до use case: `IngestController` извлекает `UUID` principal (после `SourceApiKeyAuthenticationFilter`) и формирует команду. Перегрузка `ingest(request, UUID)`, которую использует Kafka-listener, становится единственным путём.
- `RawEventStorePort` — `save(RawEvent) : UUID`, `findById(UUID)`, `count()`; `ObjectMapper`-сериализация `attributes`/`raw_payload` в jsonb уходит в `RawEventJpaMapper` (адаптер). Учёт `accepted`/`rejected` остаётся в use case: `try/catch` вокруг вызова порта сохраняется, чтобы счётчики совпадали с текущим поведением.
- `EventSourceStatePort` — `find(UUID) : Optional<SourceIngestState>`, `markSuccess(UUID, adapterVersion, Instant)`; реализация в `adapter/out/persistence` делегирует существующему `configuration.persistence.EventSourceRepository` (контекст `configuration` не трогаем).
- `ProcessRawEventBatchPort` (outbound в `ingestion`) → реализуется `ProcessRawEventBatchAdapter`, который вызывает inbound-порт `processing`. Это устраняет прямой импорт `processing.service.EventProcessingService` из `ingestion`.
- `RawEventKafkaListener` перестаёт сам читать `EventSourceRepository`: проверка «неизвестный/неактивный источник» переезжает в use case (или в порт `EventSourceStatePort`), логирование и политика commit/skip остаются в адаптере — наблюдаемое поведение (skip+commit для permanent, проброс исключения для transient) не меняется.
- `RawEventQueryService` (`GET /api/v1/raw-events`) — переносится в `application/service` с портом постраничного чтения; DTO остаются в `adapter/in/web`.

Транзакции (решение «как есть»): `@Transactional` снимается с `IngestEventsService` и ставится на входные адаптеры (`IngestController.ingest`, `RawEventKafkaListener.onMessage`), либо объявляется декларативно в `infrastructure/config`. Итог тот же: одна транзакция на `ingest + processBatch`, откат при непойманном исключении, отсутствие commit оффсета в Kafka. Выбор способа — решение архитектора; ADR запрещает Spring-аннотации в `application`.

### 1.3 ArchUnit (вводится вместе с Phase 1)

Зависимость `com.tngtech.archunit:archunit-junit5` (`test` scope) в **оба** pom: `backend/adapter/pom.xml`, `backend/fm-module/pom.xml`.

Тесты:

- `backend/adapter/src/test/java/com/wisla/fm/adapter/architecture/HexagonalArchitectureTest.java`
- `backend/fm-module/src/test/java/ru/wisla/fm/architecture/HexagonalArchitectureTest.java`

Правила (в fm-module — **только** для пакетов `ru.wisla.fm.ingestion..` и `ru.wisla.fm.processing..`, чтобы немигрированные контексты не падали):

1. `..domain..` не зависит от `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `com.fasterxml.jackson..`, `org.apache.kafka..`, `org.springframework.kafka..`, `jakarta.servlet..`.
2. `..application..` (порты и сервисы) не зависит от тех же пакетов и не зависит от `..adapter..` / `..infrastructure..`.
3. `..domain..` не зависит от `..application..` и `..adapter..`.
4. Классы с `@Entity` / `@Table` внутри scope — только в `..adapter.out.persistence..`.
5. Интерфейсы, наследующие `org.springframework.data.repository.Repository`, — только в `..adapter.out.persistence..`.
6. `@RestController` / `@KafkaListener` / `@Scheduled` — только в `..adapter.in..`.
7. `layeredArchitecture()` для каждого контекста: `domain ← application ← adapter`, `infrastructure` может зависеть от всех.

### 1.4 Spring-free use-case тесты (Phase 1)

`backend/adapter` (обычный JUnit 5, без `@SpringBootTest`, с in-memory фейками портов):

- `ReceiveWebhookEventServiceTest` — 404 `unknown_source`, истёкший snapshot, 401 `invalid_source_key` при несовпадении header/query, 401 `missing_api_key`, 401 неверный ключ, 403 `source_blocked`, 400 `filtered`, `forwarded`, `buffered` при retryable-ошибке Kafka, 502 `ingest_rejected` при permanent.
- `RetryBufferedEventsServiceTest` — success → удаление, permanent → удаление, retryable → `scheduleRetry`, отсутствующая конфигурация → перепланирование.
- `FilterRulesTest` — `enabled=false`, `drop_if`, `pass_only`, все операторы, dotted-path, отсутствующее поле.
- `IngestPayloadNormalizerTest` — `probe=true` → heartbeat-body, `event_nseverity` 1..5, `event_value=0` → `status=closed`, приоритет полей `externalId`/`title`/`occurredAt`, fallback severity.
- `BufferedEventTest` — экспоненциальный backoff с ограничением `2^10`.
- `SyncSourceConfigServiceTest` — upsert, `blocked = status != active`, TTL +86400s, ошибка порта не бросает исключение.

`backend/fm-module` (`ingestion`):

- `IngestEventsServiceTest` — heartbeat-ветка (0 accepted, обновление `adapterVersion`/`lastSuccessAt`), пакет событий (accepted/rejected/`rawEventIds`, один `ingestBatchId`), неизвестный источник → `IllegalArgumentException`, вызов `ProcessRawEventBatchPort` только при непустом списке, `status` по умолчанию `new`.
- `RawEventQueryServiceTest` — постранично, сортировка по `createdAt desc`.

## What changes — Phase 2 (ядро обработки)

```text
ru.wisla.fm.processing/
  domain/                 Event, IncomingRawEvent, SeverityRank, DedupPolicy, DedupKey,
                          ThresholdPolicy, CorrelationPolicy, ProcessingDecision (+Intents),
                          CompiledRulePlan, RuleGraph, RuleNode, RuleEdge, RuleDefinition
  domain/service/         RuleGraphTraverser, RuleConditionEvaluator, SwitchBranchSelector,
                          RuleCanvasCompiler, EventFactory, DedupMerger,
                          ThresholdEvaluator, CorrelationEvaluator, PushMessageRenderer
  application/port/in/    ProcessRawEventBatchUseCase
  application/port/out/   EventStorePort, RawEventStatePort, CiLookupPort,
                          RuleDefinitionPort, NotificationPort, PushNotificationPort
  application/service/    ProcessRawEventBatchService
  adapter/out/persistence/ EventJpaEntity, EventActionLogJpaEntity, EventJpaRepository,
                          EventActionLogJpaRepository, EventPersistenceAdapter, EventJpaMapper
  adapter/out/ingestion/  RawEventStateAdapter
  adapter/out/cmdb/       CiLookupAdapter
  adapter/out/rules/      RuleDefinitionAdapter (парсинг canvas JSON + plan-кеш + last_run_at)
  adapter/out/notification/ NotifyAdapter, PushNotificationAdapter
  api/, service/          console-use-cases (EventQueryService, EventActionService,
                          EventUpdateService, EventController) — остаются на месте,
                          получают только механическое обновление типов
  infrastructure/config/
```

Inbound-порт: `ProcessRawEventBatchUseCase.process(List<UUID> rawEventIds)` — контракт, который дергает `ingestion`. Реализация `ProcessRawEventBatchService` повторяет текущий алгоритм `EventProcessingService.processBatch/processRawEvent` (включая `try/catch` с записью `processing_error` в `raw_events` и пропуск уже обработанных).

Outbound-порты:

| Порт | Методы | Реализация |
|---|---|---|
| `RawEventStatePort` | `findById(UUID)`, `markProcessed(id, eventId, ciId)`, `recordError(id, message)` | `adapter/out/ingestion/RawEventStateAdapter` → делегирует `RawEventJpaRepository` (единственный JPA-маппинг `raw_events` сохраняется в `ingestion`) |
| `EventStorePort` | `save(Event)`, `findById(UUID)`, `findActiveDuplicate(DedupKey)`, `countRecentBySeverity(...)`, `existsRecentByTitle(...)`, `findWindow(...)` | `adapter/out/persistence/EventPersistenceAdapter` — внутри **дословно** повторяет выбор между `ciId != null` и `CiIdIsNull` производными запросами |
| `CiLookupPort` | `findOrCreateByFqdn(String) : Optional<CiSnapshot>` | `adapter/out/cmdb/CiLookupAdapter` → `cmdb.service.CiService` |
| `RuleDefinitionPort` | `findEnabledRules() : List<RuleDefinition>`, `markRun(Set<UUID>, Instant)` | `adapter/out/rules/RuleDefinitionAdapter` → `rules.persistence.ProcessingRuleRepository`; здесь же `ObjectMapper`-парсинг canvas JSON и plan-кеш (сегодня — в `RuleCanvasEngine`) |
| `NotificationPort` | `notify(ruleId, channel, emailAddress)` | `adapter/out/notification/NotifyAdapter` → `notifications.api.NotifyStubService` |
| `PushNotificationPort` | `createPush(ruleId, eventId, title, message)` | `adapter/out/notification/PushNotificationAdapter` → `notifications.api.PushNotificationService` |

Доменные модели и JPA-двойники (Phase 2):

| Доменная модель | Поведение | JPA-класс | Таблица |
|---|---|---|---|
| `Event` | `fromRawEvent(...)`, `synthetic(...)`, `registerRepeat(now)`, `escalateSeverity(candidate)`, `assignRoot(id)` | `EventJpaEntity` | `events` |
| `IncomingRawEvent` | read-model raw-события для движка (без импорта `ingestion.domain`) | — (мапится из `RawEventJpaEntity`) | `raw_events` |
| `SeverityRank` | порядок `fatal < critical < major < minor < warning < …` (из `DedupService.severityRank`) | — | — |
| `DedupPolicy` / `DedupKey` | из `DedupConfig` (`useSource/useTitle/useCi`, `fromKey`) | — | — |
| `ThresholdPolicy`, `CorrelationPolicy` | из `ThresholdConfig`, `CorrelationConfig` | — | — |
| `ProcessingDecision` + `ThresholdIntent`/`CorrelationIntent`/`NotifyIntent`/`PushIntent` | перенос как есть (уже чистые records) | — | — |
| `RuleGraph` / `RuleNode` / `RuleEdge` / `CompiledRulePlan` | из `CanvasNodeView`, `CanvasEdgeView`, `CompiledRulePlan` | — | — |
| — (доменной модели пока нет) | — | `EventActionLogJpaEntity` (механический перенос, чтобы правило ArchUnit «`@Entity` только в `adapter/out/persistence`» выполнялось для всего контекста) | `event_action_logs` |

Доменные сервисы, извлекаемые из Spring-бинов: `RuleCanvasCompiler`, `RuleConditionEvaluator`, `SwitchBranchSelector`, обход графа из `RuleCanvasEngine` (`RuleGraphTraverser`), правило слияния из `DedupService`, правило порога и генерации synthetic-события из `ThresholdService`, правило корреляции из `CorrelationService`, шаблон push-сообщения (`resolvePushMessage`) из `EventProcessingService`.

Устранение обратных зависимостей:

- `notifications.api.NotifyStubService.execute(ProcessingDecision.NotifyIntent)` → сигнатура меняется на примитивы (`ruleId`, `channel`, `emailAddress`); импорт `processing.canvas` из `notifications` исчезает. Поведение — по-прежнему no-op stub.
- `rules.api.RuleCanvasValidator` импортирует `processing.canvas.CanvasNodeView/CanvasEdgeView`. Рекомендация: завести локальные view-records внутри `rules` (валидатор перестаёт зависеть от `processing`), REST-контракт и текст ошибок валидации не меняются. Альтернатива — задокументировать отклонение в `design.md`. Решение — за архитектором.

Spring-free use-case тесты (Phase 2):

- `ProcessRawEventBatchServiceTest` — фейки всех шести портов: создание события из raw, привязка CI и `system/subsystem`, dedup-ветка vs прямое сохранение, threshold-intent → вызов оценщика, correlation-intent → установка `rootEventId`, notify/push-intents, `markRun` для исполненных правил, `markProcessed`, пропуск `processed=true`, исключение внутри обработки → `recordError` без падения всего batch.
- `RuleGraphTraverserTest` / `RuleCanvasCompilerTest` / `RuleConditionEvaluatorTest` / `SwitchBranchSelectorTest` — уже почти Spring-free, переводятся на доменные типы; добавить legacy-fallback (`dedup`/`threshold`/`correlation` без canvas) и обрыв обхода на ложном `condition`.
- `DedupMergerTest` — `repeat_count++`, `last_repeat_at`, эскалация severity только «вверх», отсутствие ключей → создание нового события.
- `ThresholdEvaluatorTest` — только `critical`, окно, порог, идемпотентность по synthetic-заголовку `"Threshold: N+ critical events in M minutes"`, `severity=fatal`, `attributes={"synthetic":true,"ruleType":"threshold"}`.
- `CorrelationEvaluatorTest` — matchField `title`/`severity`/`source`, `windowMin`, root = первое событие окна, переход к существующему `rootEventId`, отсутствие самоссылки.
- `EventFactoryTest`, `PushMessageRendererTest` (`{title}`, `{severity}`, дефолт «Событие»).

## Modules (для `state.modules`)

```json
["backend/adapter", "backend/fm-module"]
```

## Non-goals (явно)

- Никаких изменений REST-контрактов: пути, методы, коды ответов, имена полей DTO, коды ошибок.
- Никаких изменений Kafka: имя топика, ключ, схема `RawEventEnvelope`, `schemaVersion`, consumer group, политика commit.
- Никаких изменений схемы БД: ни одного нового/изменённого Liquibase changeset, ни одного переименования таблицы/колонки.
- Frontend (`frontend/`) не затрагивается.
- `backend/zabbix-simulator` не затрагивается.
- `prototype/` не затрагивается.
- MapStruct не вводится — мапперы рукописные.
- Никакого разделения сервисов, новых деплоймент-юнитов, общего domain-JAR между сервисами.
- Никакой миграции тестов на Testcontainers; существующие `@SpringBootTest` + H2 и `@EmbeddedKafka` остаются как регрессионная сетка.
- Контексты `identity`, `console`, `dashboard`, `admin`, `settings`, `health`, `configuration`, `cmdb`, `rules`, `notifications` не переводятся на гексагональную структуру (только механические правки типов/сигнатур, перечисленные выше).
- Транзакционные границы, поведение при ошибках, ретраи и идемпотентность не меняются.
- Производительность и запросы к БД не оптимизируются (набор SQL-запросов остаётся тем же).

## Frozen contracts (регрессия обязана доказать неизменность)

REST — `backend/adapter`:

| Endpoint | Замороженное |
|---|---|
| `POST /webhook/{sourceKey}` | 202 `WebhookAcceptedResponse{accepted, delivery, message_id, ingest_status}` (`JsonInclude.NON_NULL`); `delivery ∈ {forwarded, buffered}`; заголовок `X-Source-Key` и query `sourceKey` |
| ошибки webhook | 400 `invalid_json`, 400 `filtered`, 401 `missing_api_key`, 401 `invalid_source_key`, 403 `source_blocked`, 404 `unknown_source`, 413 `payload_too_large`, 502 `ingest_rejected` |
| `GET /health` | `HealthResponse{status, version, database, fm_module…}`; 503 при `database=down`, иначе 200 |
| `GET /internal/sources/{sourceId}/config` | `SourceConfigSnapshotDto`, 404 `config_not_found`, 401 `unauthorized` при неверном `Authorization: Bearer <internal token>` |
| `POST /internal/probe` | `ProbeResponse`, включая `latency`, `delivery`, `ingest_status` |
| `POST /internal/config/sync` | 202 без тела |

REST — `backend/fm-module`:

| Endpoint | Замороженное |
|---|---|
| `POST /api/v1/ingest` | 202 `IngestResponse{accepted, rejected, rawEventIds, heartbeatAck}`; аутентификация source-API-key (`SourceApiKeyAuthenticationFilter.INGEST_PATH`); `heartbeat=true` → `(0,0,[],true)` |
| `GET /api/v1/raw-events` | `RawEventPage` + `PageMeta`, сортировка `createdAt desc` |
| `GET /api/v1/events`, `GET /api/v1/events/{id}`, `PATCH /api/v1/events/{id}`, `POST /api/v1/events/{id}/actions` | `EventPage`/`EventDetailDto`/`EventDto`/`EventActionResult`, набор сортировок (`createdAt, lastRepeatAt, repeatCount, severity, status, title, nodeFqdn, systemName`), кастомная сортировка по severity и nulls-порядок `lastRepeatAt` |
| `GET /api/v1/internal/sources` | контракт, который читает adapter (`X-Service-Key`) |

Kafka:

- Топик `fm.raw-events` (`WISLA_KAFKA_RAW_EVENTS_TOPIC` / `KAFKA_RAW_EVENTS_TOPIC`), ключ = `sourceId.toString()`.
- Значение: `RawEventEnvelope{schemaVersion=1, messageId, producedAt, sourceId, sourceKey, body}`, `JsonInclude.NON_NULL`, `body` = `IngestRequest`; API-секрет источника в payload не попадает.
- Политика listener: unparseable/невалидный envelope/неизвестный или неактивный источник → log + skip (commit); `IllegalArgumentException` → skip (commit); прочие RuntimeException → проброс (без commit, redelivery).

Таблицы БД (DDL не меняется):

- `backend/adapter`: `source_config_snapshots`, `buffered_messages` (в т.ч. jsonb-колонки `filter_rules`, `payload`, колонка `ingest_api_key`).
- `backend/fm-module`: `raw_events`, `events`, `event_action_logs`, `event_sources`, `configuration_items`, `processing_rules`, `rule_push_notifications` (jsonb: `raw_events.payload`, `raw_events.raw_payload`, `events.tags`, `events.attributes`).

Поведенческие инварианты:

- `ingest → processBatch` — одна транзакция; ошибка обработки конкретного raw-события пишется в `raw_events.processing_error` и **не** откатывает транзакцию (исключение поглощается).
- Экспоненциальный backoff буфера: `base * 2^min(retryCount-1, 10)`.
- Нормализация severity и распознавание zabbix-recovery (`event_value=0` → `status=closed`).
- Идемпотентность synthetic threshold-события по заголовку в пределах окна.

## Acceptance criteria

1. `cd backend/adapter && mvn test` и `cd backend/fm-module && mvn test` — зелёные; ни один существующий тест не удалён и не ослаблен (допускаются только правки импортов/имён типов).
2. В `backend/adapter` появились пакеты `…ingest/domain`, `…ingest/application/port/{in,out}`, `…ingest/application/service`, `…ingest/adapter/{in,out}`, `…ingest/infrastructure/config`; в `backend/fm-module` — то же для `ru.wisla.fm.ingestion` и `ru.wisla.fm.processing`.
3. Ни один класс в `..domain..` и `..application..` этих контекстов не импортирует `org.springframework`, `jakarta.persistence`, `org.hibernate`, `com.fasterxml.jackson`, `org.apache.kafka`, `jakarta.servlet`; это доказано ArchUnit-тестами (правила 1–7), а не только ревью.
4. Все `@Entity` in-scope контекстов лежат в `..adapter.out.persistence..` и называются `*JpaEntity`; таблицы и имена колонок совпадают с текущими (проверяется существующими интеграционными тестами на H2 + Liquibase без новых changeset).
5. Для каждого исходящего побочного эффекта in-scope use-case есть порт в `application/port/out` и ровно одна реализация в `adapter/out` (проверяется в code review по `design.md`).
6. Существуют и проходят Spring-free unit-тесты (без `@SpringBootTest`, без загрузки контекста) для: `ReceiveWebhookEventService`, `RetryBufferedEventsService`, `SyncSourceConfigService`, `IngestEventsService`, `ProcessRawEventBatchService`, а также для извлечённых доменных сервисов (filter, normalizer, dedup, threshold, correlation, обход графа правил).
7. `ingestion` не импортирует `processing.*` напрямую (только через `ProcessRawEventBatchPort`); `notifications` и `rules` не импортируют `processing.*` (или отклонение явно задокументировано в `design.md`).
8. `git diff` не содержит изменений в: `backend/*/src/main/resources/db/**`, `docs/**/api.yaml`, `frontend/**`, `prototype/**`, `backend/zabbix-simulator/**`, `backend/docker-compose*.yaml`, `backend/docker/**`.
9. `mvn -pl backend/adapter dependency:list` / pom-диффы показывают только добавление ArchUnit (test scope); MapStruct и Testcontainers отсутствуют.
10. Регрессия замороженных контрактов подтверждена существующими тестами: `WebhookControllerTest`, `InternalControllerTest`, `HealthControllerTest`, `BufferRetryWorkerTest`, `RawEventKafkaPublisherTest`, `RawEventEnvelopeCodecTest`, `IngestControllerTest`, `IngestServiceTest`, `RawEventKafkaConsumerTest`, `RawEventKafkaListenerTest`, `RawEventEnvelopeTest`, `EventControllerTest`, `RuleCanvasRuntimeIntegrationTest`, `RuleCanvas*Test`, `CorrelationServiceTest`, `PushNotification*Test`, `DashboardControllerTest`, `SourceControllerTest`, `AdminControllerTest`, `ProductHealthControllerTest`, `RuleControllerTest`.

## Flags

| Область | Вывод |
|---|---|
| Liquibase / SQL | **Изменений нет.** Ни одного нового changeset; `db.changelog-master.yaml` в обоих сервисах не меняется. |
| REST / OpenAPI | **Изменений нет.** Пути, методы, статусы, DTO и коды ошибок заморожены; `docs/**/api.yaml` не правится. |
| Kafka | **Изменений нет.** Топик `fm.raw-events`, ключ, схема envelope, consumer group, политика commit — без изменений. |
| Docker Compose / `backend/docker/**` | **Изменений нет.** Переменные окружения и порты те же. |
| Frontend (`frontend/`) | **Изменений нет.** Поэтому `tests.frontend = skipped`, `tests.frontend_e2e = skipped`, `codeReview.frontend.status = skipped`. |
| Prototype (`prototype/`) | **Изменений нет.** |
| `backend/zabbix-simulator` | **Изменений нет.** |
| Конфигурация приложений (`application.yml`) | Изменений не планируется; при переносе `@ConfigurationProperties` префиксы (`wisla.adapter.*`, `wisla.kafka.*`) сохраняются. |
| Новые зависимости | Только `archunit-junit5` (test scope) в двух pom. |

## Migration strategy — рекомендация

**Рекомендуется прямой перенос (direct move) в рамках одного change, без strangler-обвязки из временных делегирующих `@Deprecated`-классов.**

Обоснование:

1. Сервисы деплоятся как единое приложение, внешних потребителей Java-типов нет — публичного API у классов нет, поэтому «мягкая» деprecation-фаза не даёт совместимости, которую нужно было бы сохранять.
2. Дублирование `@Entity`-классов на одну и ту же таблицу (`events`, `raw_events`, `source_config_snapshots`) в переходный период опасно: два Hibernate-маппинга одной таблицы дают риск разных snapshot-состояний в одной транзакции, конфликтов dirty-checking и труднообъяснимых расхождений `repeat_count`/`processed`. Это прямо противоречит требованию «без изменения поведения».
3. Delegating-обёртки для `@Transactional`-сервисов размывают транзакционные границы (proxy-in-proxy), а решение пользователя — сохранить границы точно как есть.
4. ArchUnit включается в этом же change: временные legacy-классы в `domain`/`application` немедленно ломали бы правила и потребовали бы исключений, которые потом придётся снимать.

Как безопасно выполнить прямой перенос:

- Порядок: Phase 1 adapter → Phase 1 fm-module `ingestion` → ArchUnit → Phase 2 `processing`. После каждого шага полный `mvn test` соответствующего модуля.
- Внутри шага: сначала создать доменные модели + порты + мапперы и провести use case на портах (новые Spring-free тесты — TDD), затем переключить входные адаптеры, затем удалить старый класс. Один Git-коммит на связанный переезд, чтобы `git diff` оставался читаемым для ревью.
- Механические обновления импортов у out-of-scope потребителей (`dashboard`, `admin`, `configuration`, `health`, `config/DevDataSeeder`, console-сервисы `processing`) выполняются одним отдельным коммитом «rename only».
- Единственное допустимое исключение из «direct move»: если ArchUnit окажется несовместим с текущей `java.version = 25` (см. риски), Phase 2 не блокируется — ArchUnit выносится в отдельный follow-up с эскалацией на gate.

## Risks / mitigations

| # | Риск | Митигация |
|---|---|---|
| 1 | **Транзакционная граница.** Перенос `@Transactional` с `IngestService` на входные адаптеры может изменить момент старта/коммита транзакции и поведение при исключении (в т.ч. отсутствие commit оффсета в Kafka). | Явно зафиксировать в `design.md` выбранный механизм (аннотация на адаптере либо AOP в `infrastructure/config`). Регрессия: `IngestControllerTest`, `RawEventKafkaConsumerTest`, `RawEventKafkaListenerTest`. Добавить тест «исключение в processing → откат raw-события, кроме записанного `processing_error`». Не менять `propagation`/`readOnly`/`isolation`. |
| 2 | **Производные dedup-запросы Spring Data в `EventRepository`.** Логика `DedupService.findActiveDuplicate` выбирает между `findFirstBySourceIdAndTitleAndCiIdAndStatusIn` и `…CiIdIsNullAndStatusIn`, причём при `useCi=false` намеренно используется вариант с `CiIdIsNull` — неочевидное поведение, которое легко «починить» при рефакторинге. | Перенести ветвление в `EventPersistenceAdapter` дословно, без «улучшений». Сначала написать характеризующие Spring-free тесты `DedupMerger`/адаптера на все 4 комбинации (`useSource/useTitle/useCi`, `ciId = null/не null`), и только затем двигать код. То же — для 6 window-запросов `CorrelationService` и 4 счётчиков `ThresholdService`. |
| 3 | **`UserEntity` с `FetchType.EAGER` для `roles` (`user_roles`, `roles`).** Контекст `identity` вне scope, но `EventActionService`/`EventQueryService` читают `UserRepository`; изменение границ транзакции или lazy/eager-контекста может дать `LazyInitializationException` или лишние join'ы. | `identity` не трогаем вообще; доступ к пользователю оставляем через существующий `UserRepository` внутри console-сервисов. В Phase 2 движок обработки к `identity` не обращается — новых портов к нему не вводим. Проверка: `AuthControllerTest`, `EventControllerTest`, `AdminControllerTest`. |
| 4 | **Колонки `@JdbcTypeCode(SqlTypes.JSON)`.** `raw_events.payload/raw_payload`, `events.tags/attributes` — `String` с jsonb; `source_config_snapshots.filter_rules`, `buffered_messages.payload` — `Map<String,Object>`. При разделении домена и JPA легко изменить тип (String↔Map) и сломать сериализацию/H2-совместимость. | В `*JpaEntity` сохранить типы полей, `columnDefinition`, `@JdbcTypeCode` **байт-в-байт**. Конверсию делать только в рукописном маппере; домен `Event` хранит `attributes` тем же `String`-JSON, чтобы не менять формат записи. Регрессия — существующие H2-интеграционные тесты + `RuleCanvasRuntimeIntegrationTest`. |
| 5 | **Кросс-контекстные импорты.** `notifications → processing` (`NotifyStubService`), `rules → processing` (`RuleCanvasValidator`), `processing → ingestion` (`RawEventEntity`), `processing → cmdb/rules/notifications`. | Ввести порты и адаптеры (`CiLookupPort`, `RuleDefinitionPort`, `NotificationPort`, `PushNotificationPort`, `RawEventStatePort`); обратные зависимости убрать сменой сигнатуры `NotifyStubService` на примитивы и локальными view-типами в `rules`. ArchUnit-правило: `..application..` не зависит от чужих контекстов. Каждое сознательное отклонение фиксируется в `design.md`. |
| 6 | **ArchUnit vs `java.version = 25`.** ArchUnit читает байт-код через ASM; class file version 69 может не поддерживаться выбранной версией. | На первом же шаге добавить зависимость и запустить пустой ArchUnit-тест. Если байт-код не парсится — взять новейшую версию ArchUnit; если и она не поддерживает Java 25, эскалировать на gate: ArchUnit выносится в follow-up change, ADR-соответствие на этом этапе проверяется code review. Не понижать `java.version` production-кода. |
| 7 | **Blast radius переименования `EventEntity` → `EventJpaEntity`.** Затронуты 8+ классов вне scope (`DashboardService`, `AdminService`, `SourceService`, `ProductHealthService`, `DevDataSeeder`, `EventQueryService`, `EventActionService`, `EventUpdateService`). | Отдельный «rename only» коммит без изменения логики; эти классы продолжают работать с JPA-репозиторием напрямую (задокументированное отклонение: они не переводятся на порты в этом change). Регрессия — их существующие контроллерные тесты. |
| 8 | **Plan-кеш `RuleCanvasEngine`** — несинхронизированный `HashMap` в singleton-бине, ключ `ruleId` + `updatedAt`. При переносе в `RuleDefinitionAdapter` легко случайно изменить семантику инвалидации или «улучшить» потокобезопасность. | Перенести кеш в адаптер с идентичной семантикой (ключ + сравнение `updatedAt`); изменение потокобезопасности — отдельный follow-up, не в этом change. Регрессия — `RuleCanvasRuntimeIntegrationTest` (обновление правила → перекомпиляция). |
| 9 | **`IngestPayloadMapper` содержит нетривиальную нормализацию** (маппинг zabbix severity, `event_value=0`, приоритет полей, парсинг `occurredAt`) — форматирование файла «раздуто» пустыми строками, риск потерять ветку при переносе. | Сначала покрыть характеризующими unit-тестами весь switch severity и все fallback-цепочки, затем переносить в домен. Сверка: `WebhookControllerTest` + новые `IngestPayloadNormalizerTest`. |
| 10 | **Учёт `accepted`/`rejected` в `IngestService`** сейчас зависит от того, что исключение сериализации/сохранения возникает внутри цикла. | Оставить `try/catch` вокруг вызова `RawEventStorePort.save` в use case; маппер-исключения должны прокидываться через порт. Тест: событие с невалидными атрибутами → `rejected = 1`, остальные приняты. |
| 11 | **Объём работ.** Две фазы + ArchUnit + полный JPA split в одном change — риск «недоделанного» состояния и незелёной сборки. | Жёсткая последовательность шагов с обязательным зелёным `mvn test` на каждом (см. Migration strategy). Phase 2 не начинается до зелёной Phase 1 + ArchUnit. При переполнении бюджета — эскалация на gate с предложением отделить Phase 2 в отдельный change. |

## Open questions для orchestrator (не блокируют gate)

1. `rules.api.RuleCanvasValidator`: локальные view-типы внутри `rules` (рекомендуется) или задокументированное отклонение с импортом `processing.domain`?
2. Механизм транзакции для `ingestion`: `@Transactional` на входных адаптерах (рекомендуется) или AOP-объявление в `infrastructure/config`?
3. Корневое имя контекста в `backend/adapter`: `com.wisla.fm.adapter.ingest` (рекомендуется, разводит слово «adapter» как сервис и как слой) или оставить пакеты на уровне `com.wisla.fm.adapter`?

## Жёсткое ограничение: независимость сервисов

- `backend/adapter` и `backend/fm-module` — два независимых микросервиса. Между ними НЕТ и не появится компиляционной зависимости: ни Maven-зависимости одного модуля на другой, ни общего domain/contract JAR, ни общих Java-классов (DTO, envelope, enum, константы).
- Взаимодействие только через сетевые контракты: Kafka topic `fm.raw-events` (`RawEventEnvelope`, schemaVersion=1) и HTTP internal endpoints.
- Каждый сервис владеет СВОЕЙ копией типов, описывающих контракт. Дублирование здесь — осознанная цена независимости, а не недостаток.
- Порт `FmModuleSourceConfigPort` (и любые другие порты adapter, ведущие к fm-module) — это outbound-порт к ВНЕШНЕЙ системе; его реализация живёт в `adapter/out/http` и общается по HTTP. Никакого импорта классов fm-module.
- Рефакторинг НЕ должен привести к появлению shared-модуля «чтобы не дублировать envelope».
- Это ограничение проверяется на code review; при подключении ArchUnit — правилом, запрещающим импорты `ru.wisla.fm..` из `com.wisla.fm.adapter..` и наоборот.

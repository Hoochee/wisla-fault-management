# Scope discovery — hexagonal-architecture

## Проблема

Текущие соглашения WISLA описывают bounded contexts и deployable-сервисы, но не фиксируют обязательные правила зависимостей внутри backend-контекстов. Поэтому новые изменения могут смешивать доменную логику, Spring/JPA/HTTP-интеграции и конфигурацию, что затрудняет тестирование и последующую эволюцию модулей.

## Что изменится

1. Добавляется ADR `docs/adr/ADR-001-hexagonal-architecture.md` с правилом зависимостей: domain не зависит от Spring, JPA, Jackson, Kafka и HTTP; application зависит от domain и портов; adapters реализуют порты; Spring wiring находится в infrastructure/config.
2. Документируется целевая структура пакетов каждого bounded context: `domain`, `application/port/in`, `application/port/out`, `adapter/in`, `adapter/out`, `infrastructure/config`.
3. Обновляются инструкции architect, backend engineer, code reviewer и `/build-feature`, чтобы будущие фичи проектировались, реализовывались и проверялись по этим правилам.
4. В OpenSpec-шаблоны и checklist добавляется обязательное описание use cases, inbound adapters, outbound ports, реализаций адаптеров и use-case тестов без Spring.

## Модули

- `docs/` — ADR и документация целевой структуры.
- `.agents/` — инструкции 05, 07 и 09.
- `build-feature/` — ядро workflow и шаблон/checklist проектирования.
- `openspec/` — шаблоны/checklist для design-артефактов.

`backend/fm-module`, `backend/adapter`, `backend/zabbix-simulator`, `frontend/` и `prototype/` не изменяются. Кодовый ingest refactor, миграция существующих пакетов и ArchUnit не входят в данный change.

## Критерии приёмки

- ADR существует и однозначно задаёт разрешённые направления зависимостей и ответственность слоёв.
- Для каждого bounded context задокументирована одинаковая целевая структура пакетов.
- Инструкции агентов и `/build-feature` требуют hexagonal-подход при новых backend-изменениях.
- OpenSpec design-checklist требует все шесть архитектурных элементов и unit-тесты use case без Spring.
- Существующая production-логика, API, базы данных, Docker Compose и UI остаются без изменений.

## Явные non-goals

- Массовое перемещение пакетов, JPA-сущностей или иной существующей production-логики.
- Новые deployable-сервисы или общий domain-модуль между `adapter` и `fm-module`.
- Полная переработка Kafka/ingest pipeline.
- ArchUnit как автоматическое enforcement-правило.
- Изменения Liquibase, REST/OpenAPI, Docker Compose, Angular SPA или React prototype.

# OpenSpec — краткое руководство для команды WISLA Fault Management

## Что это

OpenSpec — слой спецификаций перед кодом. Каждая задача проходит через артефакты в `openspec/changes/<name>/`:

```
proposal.md  →  specs/  →  design.md  →  tasks.md  →  код  →  archive
   зачем         что          как          шаги
```

Контекст проекта: `openspec/config.yaml` (TDD, модули, ссылки на `AGENTS.md` и `README.md`).

Существующие specs в `openspec/specs/` — **не удалять** и не перезаписывать вручную; обновлять через `/opsx:sync` после merge.

---

## Установка

### Требования

- **Node.js 20+** — проверка: `node -v`
- **Java 21+** + Maven — для локальных backend-тестов
- **Docker / Docker Compose** — локальный стек
- **Cursor** — slash-команды `/opsx:*` и `/build-feature`

### CLI (один раз на машину)

```powershell
npm install -g @fission-ai/openspec@latest
openspec.cmd --version
```

> На Windows PowerShell может блокировать `openspec.ps1` (политика выполнения скриптов).
> Используйте **`openspec.cmd`** вместо `openspec`, либо один раз:
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
> ```

### Проект — уже настроен

В репозитории уже есть:

```
openspec/                  # config.yaml, specs/, changes/
.cursor/commands/          # slash-команды /opsx:* и /build-feature
.cursor/skills/            # skills для агента
build-feature/             # phase machine, playbook, question bank
.agents/                   # промпты субагентов
AGENTS.md                  # карта модулей
```

После `git clone` достаточно установить CLI и **перезапустить Cursor**.

### Первичная инициализация (новый форк без toolchain)

```powershell
cd C:\Projects\wisla-fault-management
openspec.cmd init --tools cursor
```

Создаёт структуру `openspec/` и интеграцию с Cursor. Если `openspec/specs/` и `config.yaml` уже есть — **не** запускать `init` повторно без необходимости (риск перезаписи).

### Обновление OpenSpec CLI / skills

```powershell
npm install -g @fission-ai/openspec@latest
cd C:\Projects\wisla-fault-management
openspec.cmd update
```

Обновляет skills и commands в `.cursor/`. После `update` — перезапуск Cursor.  
**Внимание:** `openspec update` может перезаписать opsx skills/commands; кастомный `/build-feature` и правила нужно проверить после обновления.

### Проверка

```powershell
openspec.cmd list
openspec.cmd validate --all
```

В Cursor: ввести `/opsx:propose` или `/build-feature` — команды должны появиться в автодополнении.

### Телеметрия (опционально)

```powershell
$env:OPENSPEC_TELEMETRY = "0"
```

---

## Slash-команды в Cursor

| Команда | Когда использовать |
|---------|-------------------|
| `/build-feature` | **Полный цикл фичи** — Jira → branch → gates → делегация агентам → тесты → archive |
| `/opsx:explore` | Идея сырая, нужно подумать, сравнить варианты |
| `/opsx:propose` | Задача понятна — создать change и все артефакты |
| `/opsx:apply` | Реализовать задачи из `tasks.md` (TDD) |
| `/opsx:sync` | Перенести delta-specs в `openspec/specs/` |
| `/opsx:archive` | Закрыть change после merge/релиза |

`/opsx:*` — точечная работа. `/build-feature` оркестрирует их внутри workflow с gates и Task-делегацией.

---

## Полный цикл через `/build-feature`

Оркестратор (skill `build-feature`) — адаптация decomposition-pattern для brownfield Fault Management.

### Фазы

```
bootstrap → discovery → design → backend → backend_review → backend_tests
  → frontend → frontend_review → frontend_tests → review → done
```

Состояние: `openspec/changes/<name>/.feature-state.json`

### Запуск

```
/build-feature WISLA-12345
/build-feature console-column-sort
/build-feature continue
```

> Ключ Jira: по умолчанию Wellink `WISLA-*`. Если в задаче другой префикс — передайте его явно; ветка всё равно `feature/<KEY>`.

### Что делает bootstrap

1. Читает Jira (если ключ дан)
2. Создаёт ветку от `origin/main` с `--no-track`: `feature/WISLA-<n>`
3. `openspec new change "<changeName>"`
4. Пишет `.feature-state.json`, фаза `discovery`

### Модули

| Модуль | Тесты |
|--------|-------|
| `backend/fm-module` | `cd backend/fm-module && mvn test` |
| `backend/adapter` | `cd backend/adapter && mvn test` |
| `backend/zabbix-simulator` | `cd backend/zabbix-simulator && mvn test` |
| `frontend/` | `cd frontend && npm test` (Vitest); e2e: `npm run test:e2e` |
| `prototype/` | только если задача явно про прототип |

### Пример propose

```
/opsx:propose rules-notify-block

Задача: добавить блок notify в canvas правил.
Модули: backend/fm-module, frontend/
Затронуто: Liquibase (если схема), REST rules API, Angular rule-builder
Критерий готовности: unit + сценарии Given/When/Then в specs
Non-goals: изменения в prototype/, Kafka
```

### Пример apply

```
/opsx:apply rules-notify-block

Ограничения:
- TDD по config.yaml
- Минимальный diff
- mvn test в fm-module; npm test во frontend
```

---

## Проверка артефактов (после propose)

Для change `<name>`:

1. `proposal.md` — scope и non-goals согласованы?
2. `specs/` — сценарии Given/When/Then покрывают acceptance?
3. `design.md` — слои adapter / fm-module / UI?
4. `tasks.md` — TDD порядок (тест → код), группировка по модулям?

---

## Частые ошибки

| Ошибка | Как избежать |
|--------|--------------|
| Пропуск «сырой идеи» без propose | Сначала `/opsx:explore` или `/opsx:propose` |
| Explore + код в одном чате | Explore — только анализ; код — после `/opsx:apply` |
| Забытые модули | Явно перечислить `backend/*` / `frontend/` в proposal |
| Тесты в конце | TDD: тест в `tasks.md` раньше реализации |
| Не архивировали change | После merge — `/opsx:sync` + `/opsx:archive` |
| Ветка трекает `main` | Всегда `--no-track` при создании feature-ветки |

---

## Полезные файлы в репозитории

- `build-feature/` — state machine, playbook, question bank
- `.agents/` — промпты специализированных агентов
- `.cursor/skills/build-feature/` — skill для `/build-feature`
- `openspec/config.yaml` — контекст и правила для AI
- `AGENTS.md` — карта модулей
- `README.md` — домен, setup, тесты
- `.cursor/rules/` — git branch/commit, native SQL, delegation

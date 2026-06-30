# WISLA Fault Management

Модуль приёма, нормализации, обработки и отображения аварийных событий мониторинга.

Собран с помощью [decomposition-pattern](https://github.com/Hoochee/decomposition-pattern) — мультиагентного workflow для разработки продукта.

## Состав

| Путь | Описание |
|------|----------|
| `backend/` | Микросервисы: `fm-module`, `adapter`, `zabbix-simulator`, Docker Compose |
| `frontend/` | Angular SPA — консоль, дашборд, админка, правила |
| `docs/` | Требования, архитектура, OpenAPI, спецификации страниц |
| `prototype/` | UI-прототип (Vite + React) |
| `openspec/specs/` | Спецификации реализованных фич |

## Быстрый старт

### Требования

- Docker и Docker Compose
- Node.js 20+ (для frontend и e2e)
- Java 21 + Maven (для локальной разработки backend без Docker)

### Запуск через Docker

```bash
cd backend
docker compose up -d --build
```

Приложение: `http://localhost:8080` (API + статика frontend).

### Frontend (разработка)

```bash
cd frontend
npm install
npm start
```

### Тесты

```bash
# Backend
cd backend/fm-module && mvn test

# Frontend unit
cd frontend && npm test

# E2E (нужен запущенный backend)
cd frontend && npx playwright test
```

## Документация

- [Требования](docs/requirements.md)
- [Архитектура](docs/architecture.md)
- [API fm-module](docs/fm-module/api.yaml)
- [Сценарий демо](docs/demo-script.md)

## Workflow разработки

Новые фичи проектируются через [decomposition-pattern](https://github.com/sortedmap/decomposition-pattern): клонируйте шаблон workflow, откройте этот репозиторий как рабочую папку продукта, используйте `/build-product-feature` или OpenSpec change в среде с настроенным оркестратором.

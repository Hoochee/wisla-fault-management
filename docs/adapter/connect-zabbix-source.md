# Подключение источника Zabbix к адаптеру

Инструкция для локального стенда (`docker compose`) и ручной проверки webhook.

## Экземпляры адаптера в Compose

| Сервис | Порт на хосте | БД PostgreSQL | Назначение |
|--------|---------------|---------------|------------|
| `adapter` | **8081** | `wisla_fm_adapter` | основной (по умолчанию) |
| `adapter-2` | **8083** | `wisla_fm_adapter_2` | второй экземпляр |

Оба адаптера независимы: свой буфер, свой кэш конфигурации, один и тот же `fm-module`.

Карточка runtime на странице **Источники** (`/sources`) сейчас опрашивает только **первый** адаптер (`ADAPTER_BASE_URL` в `fm-module`, по умолчанию `http://adapter:8081`). Состояние `adapter-2` смотрите отдельно: `GET http://localhost:8083/health`.

---

## Seeded источник Zabbix (fm-module)

| Параметр | Значение |
|----------|----------|
| Имя | Zabbix Main (simulator) |
| Webhook path key | `zabbix-prod-01` |
| API key | `zabbix-demo-key` |
| URL webhook (основной адаптер) | `http://localhost:8081/webhook/zabbix-prod-01` |
| URL webhook (второй адаптер) | `http://localhost:8083/webhook/zabbix-prod-01` |

Адаптер **не хранит** источники сам — при старте и каждые ~5 минут подтягивает список из fm-module:

`GET http://fm-module:8080/api/v1/internal/sources` (с `X-Service-Key`).

После появления `adapter-2` в Compose достаточно дождаться sync (или перезапустить контейнер) — конфиг `zabbix-prod-01` подтянется автоматически.

---

## 1. Поднять стек с двумя адаптерами

```powershell
cd C:\Project\wislaFaultManagement\backend
docker compose up -d --build adapter adapter-2 fm-module zabbix-simulator
```

### Если PostgreSQL уже был создан раньше

Скрипт `init.sql` выполняется только при **первом** создании volume. Для существующего volume создайте БД вручную:

```powershell
docker exec -it backend-postgres-1 psql -U postgres -c "CREATE DATABASE wisla_fm_adapter_2;"
```

Затем перезапустите второй адаптер:

```powershell
docker compose up -d adapter-2
```

### Проверка

```powershell
curl http://localhost:8083/health
curl http://localhost:8081/health
```

Ожидаемо: `"status":"ok"`, `"fm_module":"reachable"`, `"buffered_count":0` (или небольшой хвост после сбоев).

---

## 2. Переключить zabbix-simulator на второй адаптер

Эмулятор шлёт события **только** на адаптер (не в fm-module напрямую):

```
zabbix-simulator → POST {ADAPTER}/webhook/zabbix-prod-01
                 → adapter → POST fm-module/api/v1/ingest
```

### Вариант A — через docker-compose (рекомендуется)

В `backend/docker-compose.yaml` у сервиса `zabbix-simulator` измените:

```yaml
environment:
  ADAPTER_BASE_URL: http://adapter-2:8081   # было http://adapter:8081
  SOURCE_WEBHOOK_KEY: zabbix-prod-01
  ZABBIX_SOURCE_API_KEY: zabbix-demo-key
```

И обновите `depends_on`, чтобы ждать `adapter-2`:

```yaml
depends_on:
  adapter-2:
    condition: service_healthy
```

Применить:

```powershell
docker compose up -d zabbix-simulator --force-recreate
```

### Вариант B — без правки compose (override-файл)

Создайте `backend/docker-compose.override.yaml`:

```yaml
services:
  zabbix-simulator:
    environment:
      ADAPTER_BASE_URL: http://adapter-2:8081
    depends_on:
      adapter-2:
        condition: service_healthy
```

```powershell
docker compose up -d zabbix-simulator --force-recreate
```

### Вариант C — локальный Maven-запуск симулятора

```powershell
$env:ADAPTER_BASE_URL = "http://localhost:8083"
$env:SOURCE_WEBHOOK_KEY = "zabbix-prod-01"
$env:ZABBIX_SOURCE_API_KEY = "zabbix-demo-key"
cd backend\zabbix-simulator
mvn spring-boot:run
```

---

## 3. Проверить доставку

```powershell
# health симулятора — должен показать новый URL
curl http://localhost:8082/health

# принудительный тик
curl -X POST http://localhost:8082/tick

# события в UI
# http://localhost:8080/console  (admin / admin)
```

На `/sources` у источника «Zabbix Main (simulator)» поле **Последний успех** обновится после успешного ingest (может занять один цикл опроса UI, ~30 с).

---

## 4. Ручной webhook (без симулятора)

Проверка, что **конкретный** адаптер принимает тот же источник:

```powershell
curl -X POST "http://localhost:8083/webhook/zabbix-prod-01" `
  -H "Content-Type: application/json" `
  -H "X-Source-Key: zabbix-demo-key" `
  -d '{
    "host": "demo-server.wisla.local",
    "trigger_name": "Manual test via adapter-2",
    "event_nseverity": 4,
    "event_value": 1,
    "message": "Test from curl"
  }'
```

Ответ `"delivery":"forwarded"` — событие ушло в fm-module.  
`"delivery":"buffered"` — fm-module временно недоступен, запись в буфере адаптера.

---

## 5. Вернуть симулятор на первый адаптер

```yaml
ADAPTER_BASE_URL: http://adapter:8081
depends_on:
  adapter:
    condition: service_healthy
```

```powershell
docker compose up -d zabbix-simulator --force-recreate
```

---

## Важно

1. **Не направляйте один симулятор сразу на два адаптера** — получите дубликаты событий в fm-module.
2. **Один источник — один активный webhook URL** в продакшене (в Zabbix Media Type указывается URL выбранного адаптера).
3. Поле **Endpoint** источника в UI (`/sources`) — справочное; для Push REST решает URL, куда шлёт Zabbix/симулятор. При смене адаптера имеет смысл обновить endpoint в карточке источника на `http://<host>:8083/webhook/zabbix-prod-01`.
4. API key источника (`zabbix-demo-key` в dev) проверяется на адаптере по хэшу из fm-module — менять его не нужно при переключении между `adapter` и `adapter-2`.

---

## Связанные документы

- [`docs/zabbix-simulator.md`](../zabbix-simulator.md) — формат payload и сценарии
- [`backend/adapter/README.md`](../../backend/adapter/README.md) — API адаптера
- [`docs/adapter/api.yaml`](api.yaml) — OpenAPI webhook

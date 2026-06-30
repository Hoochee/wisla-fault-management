# WISLA FM — Zabbix Simulator

Emulates **Zabbix 6.x webhook** alerts for MVP demos.

**Port:** 8082  
**Docs:** [`docs/zabbix-simulator.md`](../../docs/zabbix-simulator.md)

## Delivery chain (mandatory)

```
zabbix-simulator :8082
  → POST adapter:8081/webhook/{sourceKey}  (+ X-Source-Key)
    → adapter: mapping, filter, buffer
      → POST fm-module:8080/api/v1/ingest
```

The simulator **never** calls fm-module directly.

## Quick start

```bash
mvn spring-boot:run
curl http://localhost:8082/health
curl -X POST http://localhost:8082/tick
```

Docker Compose (full stack):

```bash
cd ../
docker compose up -d zabbix-simulator
```

Environment (Compose):

| Variable | Example |
|----------|---------|
| `ADAPTER_BASE_URL` | `http://adapter:8081` |
| `SOURCE_WEBHOOK_KEY` | `zabbix-prod-01` |
| `ZABBIX_SOURCE_API_KEY` | `zabbix-demo-key` |

Second adapter (`adapter-2`, host port **8083**): see [`docs/adapter/connect-zabbix-source.md`](../../docs/adapter/connect-zabbix-source.md).

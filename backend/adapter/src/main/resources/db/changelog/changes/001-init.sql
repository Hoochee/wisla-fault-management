--liquibase formatted sql

--changeset wisla:001-init
--validCheckSum: 9:d4c02cf8a36662581460b627d742d353
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE buffered_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID NOT NULL,
    ingest_api_key  VARCHAR(512),
    payload         JSONB NOT NULL,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_buffered_messages_source_id ON buffered_messages (source_id);

CREATE TABLE source_config_snapshots (
    source_id       UUID PRIMARY KEY,
    filter_rules    JSONB NOT NULL DEFAULT '{}',
    api_key_hash    VARCHAR(255) NOT NULL,
    endpoint        VARCHAR(512) NOT NULL,
    ttl_expires_at  TIMESTAMPTZ NOT NULL,
    source_key      VARCHAR(128) NOT NULL,
    blocked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_source_config_snapshots_source_key ON source_config_snapshots (source_key);
CREATE INDEX idx_source_config_snapshots_ttl ON source_config_snapshots (ttl_expires_at);

CREATE TABLE adapter_heartbeats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id   UUID NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_adapter_heartbeats_source_sent ON adapter_heartbeats (source_id, sent_at DESC);

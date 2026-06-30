--liquibase formatted sql
--changeset wisla:003-configuration
CREATE TABLE event_sources (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    protocol        VARCHAR(64) NOT NULL,
    endpoint        VARCHAR(512) NOT NULL,
    api_key_hash    VARCHAR(255) NOT NULL,
    api_key_prefix  VARCHAR(16) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'inactive',
    schedule        VARCHAR(64),
    filter_rules    JSONB NOT NULL DEFAULT '{}',
    parser_config   JSONB NOT NULL DEFAULT '{}',
    adapter_version VARCHAR(32),
    last_success_at TIMESTAMPTZ,
    webhook_path_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_sources_type CHECK (type IN ('push_rest','push_snmp_trap','pull_etl')),
    CONSTRAINT chk_event_sources_status CHECK (status IN ('active','inactive','blocked'))
);

CREATE INDEX idx_event_sources_status ON event_sources(status);
CREATE INDEX idx_event_sources_type ON event_sources(type);
CREATE INDEX idx_event_sources_last_success_at ON event_sources(last_success_at);

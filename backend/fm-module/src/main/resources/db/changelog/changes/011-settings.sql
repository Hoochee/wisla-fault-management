--liquibase formatted sql
--changeset wisla:011-settings
CREATE TABLE module_settings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settings_key            VARCHAR(32) NOT NULL UNIQUE DEFAULT 'default',
    timezone                VARCHAR(64) NOT NULL DEFAULT 'Europe/Moscow',
    polling_interval_sec    INTEGER NOT NULL DEFAULT 60,
    auto_archive_days       INTEGER NOT NULL DEFAULT 30,
    repeat_interval_min     INTEGER NOT NULL DEFAULT 15,
    wisla_integration       BOOLEAN NOT NULL DEFAULT FALSE,
    itsm_integration        BOOLEAN NOT NULL DEFAULT FALSE,
    notification_config     JSONB NOT NULL DEFAULT '{}',
    integration_config      JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO module_settings (settings_key) VALUES ('default');

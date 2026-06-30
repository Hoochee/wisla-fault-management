--liquibase formatted sql

--changeset wisla:003-source-config-source-key
-- source_key and unique index already created in 001-init; no-op for idempotent rollout
SELECT 1;

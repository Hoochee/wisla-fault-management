--liquibase formatted sql

--changeset wisla:004-buffered-messages-ingest-api-key
ALTER TABLE buffered_messages
    ADD COLUMN IF NOT EXISTS ingest_api_key VARCHAR(512);

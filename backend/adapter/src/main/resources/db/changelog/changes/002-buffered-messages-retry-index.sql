--liquibase formatted sql

--changeset wisla:002-buffered-messages-retry-index
CREATE INDEX idx_buffered_messages_next_retry
    ON buffered_messages (next_retry_at)
    WHERE next_retry_at IS NOT NULL;

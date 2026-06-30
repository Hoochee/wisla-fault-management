--liquibase formatted sql
--changeset wisla:007-action-logs
CREATE TABLE event_action_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    action      VARCHAR(64) NOT NULL,
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    user_name   VARCHAR(255) NOT NULL,
    details     TEXT NOT NULL,
    metadata    JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_action_logs_event_id_created ON event_action_logs(event_id, created_at DESC);

--liquibase formatted sql
--changeset wisla:014-event-duty-actions
ALTER TABLE events ADD COLUMN acknowledged_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN acknowledged_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE events ADD COLUMN silenced_until TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN silenced_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_events_silenced_until ON events (silenced_until) WHERE silenced_until IS NOT NULL;

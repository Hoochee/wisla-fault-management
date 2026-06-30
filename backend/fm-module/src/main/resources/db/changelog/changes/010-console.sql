--liquibase formatted sql
--changeset wisla:010-console
CREATE TABLE event_maps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    query           TEXT NOT NULL,
    columns         JSONB NOT NULL DEFAULT '[]',
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    is_personal     BOOLEAN NOT NULL DEFAULT TRUE,
    owner_user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_maps_owner_user_id ON event_maps(owner_user_id);
CREATE INDEX idx_event_maps_is_system ON event_maps(is_system);

INSERT INTO event_maps (name, query, columns, is_system, is_personal, sort_order)
VALUES
    ('Активные', 'status != closed', '["severity","title","nodeFqdn","createdAt"]', TRUE, FALSE, 0),
    ('Закрытые', 'status = closed', '["severity","title","nodeFqdn","closedAt"]', TRUE, FALSE, 1);

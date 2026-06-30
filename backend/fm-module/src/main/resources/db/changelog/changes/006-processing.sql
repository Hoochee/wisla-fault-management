--liquibase formatted sql
--changeset wisla:006-processing
CREATE TABLE events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status              VARCHAR(16) NOT NULL DEFAULT 'new',
    severity            VARCHAR(16) NOT NULL,
    title               VARCHAR(512) NOT NULL,
    description         TEXT,
    source_id           UUID NOT NULL REFERENCES event_sources(id),
    ci_id               UUID,
    node_fqdn           VARCHAR(512),
    system_name         VARCHAR(255),
    subsystem_name      VARCHAR(255),
    assigned_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    root_event_id       UUID,
    repeat_count        INTEGER NOT NULL DEFAULT 1,
    tags                JSONB NOT NULL DEFAULT '[]',
    attributes          JSONB NOT NULL DEFAULT '{}',
    itsm_incident_number VARCHAR(64),
    raw_event_id        UUID,
    source_at           TIMESTAMPTZ NOT NULL,
    last_repeat_at      TIMESTAMPTZ,
    taken_at            TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_events_status CHECK (status IN ('new','in_progress','closed','archived','maintenance','deferred')),
    CONSTRAINT chk_events_severity CHECK (severity IN ('fatal','critical','major','minor','warning','normal'))
);

ALTER TABLE events ADD CONSTRAINT fk_events_root_event
    FOREIGN KEY (root_event_id) REFERENCES events(id) ON DELETE SET NULL;

ALTER TABLE raw_events ADD CONSTRAINT fk_raw_events_processed_event
    FOREIGN KEY (processed_event_id) REFERENCES events(id);

ALTER TABLE events ADD CONSTRAINT fk_events_raw_event
    FOREIGN KEY (raw_event_id) REFERENCES raw_events(id);

CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_severity ON events(severity);
CREATE INDEX idx_events_created_at ON events(created_at DESC);
CREATE INDEX idx_events_source_id ON events(source_id);
CREATE INDEX idx_events_ci_id ON events(ci_id);
CREATE INDEX idx_events_status_severity_created ON events(status, severity, created_at DESC);
CREATE INDEX idx_events_active ON events(severity, created_at DESC)
    WHERE status NOT IN ('closed', 'archived');

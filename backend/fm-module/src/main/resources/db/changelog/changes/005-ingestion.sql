--liquibase formatted sql
--changeset wisla:005-ingestion
CREATE TABLE raw_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id           UUID NOT NULL REFERENCES event_sources(id),
    external_id         VARCHAR(255),
    title               VARCHAR(512) NOT NULL,
    description         TEXT,
    severity            VARCHAR(16) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'new',
    node_fqdn           VARCHAR(512),
    ci_id               UUID,
    payload             JSONB NOT NULL DEFAULT '{}',
    raw_payload         JSONB NOT NULL DEFAULT '{}',
    source_at           TIMESTAMPTZ NOT NULL,
    ingest_batch_id     UUID,
    processed           BOOLEAN NOT NULL DEFAULT FALSE,
    processed_event_id  UUID,
    processing_error    TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_raw_events_severity CHECK (severity IN ('fatal','critical','major','minor','warning','normal'))
);

CREATE INDEX idx_raw_events_source_id_created_at ON raw_events(source_id, created_at DESC);
CREATE INDEX idx_raw_events_processed ON raw_events(processed);
CREATE UNIQUE INDEX uq_raw_events_source_external ON raw_events(source_id, external_id)
    WHERE external_id IS NOT NULL;

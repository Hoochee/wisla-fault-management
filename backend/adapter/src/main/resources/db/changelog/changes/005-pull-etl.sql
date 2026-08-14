--liquibase formatted sql

--changeset wisla:005-pull-etl
ALTER TABLE source_config_snapshots
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'push_rest';

ALTER TABLE source_config_snapshots
    ADD COLUMN schedule VARCHAR(64);

ALTER TABLE source_config_snapshots
    ADD COLUMN parser_config JSONB NOT NULL DEFAULT '{}';

CREATE TABLE pull_metric_states (
    source_id       UUID NOT NULL,
    external_id     VARCHAR(512) NOT NULL,
    last_severity   VARCHAR(16),
    last_value      DOUBLE PRECISION,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source_id, external_id)
);

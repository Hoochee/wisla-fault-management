--liquibase formatted sql
--changeset wisla:008-rules
CREATE TABLE processing_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    rule_type           VARCHAR(32) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    trigger_type        VARCHAR(128) NOT NULL,
    approval_status     VARCHAR(16) NOT NULL DEFAULT 'draft',
    description         TEXT,
    canvas              JSONB NOT NULL DEFAULT '{"nodes":[],"edges":[]}',
    last_run_at         TIMESTAMPTZ,
    created_by_user_id  UUID REFERENCES users(id),
    approved_by_user_id UUID REFERENCES users(id),
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_processing_rules_type CHECK (rule_type IN ('dedup','problem_resolution','correlation','threshold')),
    CONSTRAINT chk_processing_rules_approval CHECK (approval_status IN ('approved','pending','draft'))
);

CREATE INDEX idx_processing_rules_enabled ON processing_rules(enabled);
CREATE INDEX idx_processing_rules_rule_type ON processing_rules(rule_type);
CREATE INDEX idx_processing_rules_approval_status ON processing_rules(approval_status);

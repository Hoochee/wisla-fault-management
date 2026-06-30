--liquibase formatted sql

--changeset wisla:012-rule-push-notifications
CREATE TABLE rule_push_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES processing_rules(id),
    event_id UUID REFERENCES events(id),
    title VARCHAR(512) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rule_push_notifications_created_at ON rule_push_notifications (created_at);

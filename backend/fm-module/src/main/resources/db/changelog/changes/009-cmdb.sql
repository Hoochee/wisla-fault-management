--liquibase formatted sql
--changeset wisla:009-cmdb
CREATE TABLE configuration_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fqdn            VARCHAR(512) NOT NULL UNIQUE,
    ci_type         VARCHAR(64) NOT NULL,
    system_name     VARCHAR(255) NOT NULL,
    subsystem_name  VARCHAR(255),
    software        VARCHAR(255),
    tags            JSONB NOT NULL DEFAULT '[]',
    external_ids    JSONB NOT NULL DEFAULT '{}',
    auto_created    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_configuration_items_fqdn ON configuration_items(fqdn);
CREATE INDEX idx_configuration_items_system_name ON configuration_items(system_name);
CREATE INDEX idx_configuration_items_tags ON configuration_items USING GIN (tags);

CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(64) UNIQUE,
    name                VARCHAR(255) NOT NULL,
    tenant              VARCHAR(128) NOT NULL,
    site                VARCHAR(128) NOT NULL,
    tags                JSONB NOT NULL DEFAULT '[]',
    max_severity        VARCHAR(16) NOT NULL DEFAULT 'normal',
    active_event_count  INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_products_max_severity CHECK (max_severity IN ('fatal','critical','major','minor','warning','normal'))
);

CREATE INDEX idx_products_tenant ON products(tenant);
CREATE INDEX idx_products_site ON products(site);
CREATE INDEX idx_products_max_severity ON products(max_severity);

CREATE TABLE product_ci (
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    ci_id           UUID NOT NULL REFERENCES configuration_items(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_id, ci_id)
);

CREATE INDEX idx_product_ci_ci_id ON product_ci(ci_id);

ALTER TABLE events ADD CONSTRAINT fk_events_ci
    FOREIGN KEY (ci_id) REFERENCES configuration_items(id);

ALTER TABLE raw_events ADD CONSTRAINT fk_raw_events_ci
    FOREIGN KEY (ci_id) REFERENCES configuration_items(id);

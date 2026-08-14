--liquibase formatted sql
--changeset wisla:013-product-health
CREATE TABLE product_component (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    code                VARCHAR(32) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    weight              INT NOT NULL CHECK (weight BETWEEN 0 AND 100),
    influence_type      VARCHAR(16) NOT NULL CHECK (influence_type IN ('weighted','critical')),
    critical_threshold  INT NOT NULL DEFAULT 100,
    sort_order          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, code)
);

CREATE INDEX idx_product_component_product_id ON product_component(product_id);

CREATE TABLE product_component_ci (
    component_id    UUID NOT NULL REFERENCES product_component(id) ON DELETE CASCADE,
    ci_id           UUID NOT NULL REFERENCES configuration_items(id) ON DELETE CASCADE,
    weight          INT CHECK (weight IS NULL OR weight BETWEEN 0 AND 100),
    PRIMARY KEY (component_id, ci_id)
);

CREATE INDEX idx_product_component_ci_ci_id ON product_component_ci(ci_id);

CREATE TABLE product_health_snapshot (
    product_id          UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    health_percent      INT NOT NULL,
    damage_percent      INT NOT NULL,
    max_severity        VARCHAR(16) NOT NULL,
    active_event_count  INT NOT NULL,
    payload             JSONB NOT NULL DEFAULT '{}',
    calculated_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE product_health_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    bucket_start    TIMESTAMPTZ NOT NULL,
    bucket_minutes  INT NOT NULL,
    min_health      INT NOT NULL,
    max_health      INT NOT NULL,
    worst_severity  VARCHAR(16) NOT NULL,
    UNIQUE (product_id, bucket_start)
);

CREATE INDEX idx_product_health_history_product_bucket
    ON product_health_history(product_id, bucket_start);

INSERT INTO product_component (id, product_id, code, name, weight, influence_type, critical_threshold, sort_order)
SELECT gen_random_uuid(), p.id, 'COMMON', 'COMMON', 100, 'weighted', 100, 0
FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM product_component pc WHERE pc.product_id = p.id AND pc.code = 'COMMON'
);

INSERT INTO product_component_ci (component_id, ci_id)
SELECT pc.id, pci.ci_id
FROM product_ci pci
JOIN product_component pc ON pc.product_id = pci.product_id AND pc.code = 'COMMON'
WHERE NOT EXISTS (
    SELECT 1
    FROM product_component_ci existing
    JOIN product_component owner ON owner.id = existing.component_id
    WHERE owner.product_id = pci.product_id AND existing.ci_id = pci.ci_id
);

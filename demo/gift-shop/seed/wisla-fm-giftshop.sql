-- Gift Shop CMDB + pull_etl source for wisla_fm (NOT giftshop postgres).
-- Apply after fm-module Liquibase 013-product-health.sql:
--
--   psql -h localhost -p 5432 -U wisla -d wisla_fm -f demo/gift-shop/seed/wisla-fm-giftshop.sql
--
-- Password: wisla
-- Idempotent: safe to re-run.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'product_component'
    ) THEN
        RAISE EXCEPTION 'product_component is missing — run fm-module Liquibase changeset 013-product-health.sql first';
    END IF;
END $$;

INSERT INTO products (id, code, name, tenant, site, tags, max_severity, active_event_count)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000001',
       'giftshop',
       'Gift Shop',
       'demo',
       'local',
       '["demo"]'::jsonb,
       'normal',
       0
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = 'giftshop');

INSERT INTO configuration_items (id, fqdn, ci_type, system_name, subsystem_name, software, tags, external_ids, auto_created)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000010',
       'giftshop-storefront.demo',
       'service',
       'Gift Shop',
       'Storefront',
       'giftshop-storefront',
       '["giftshop"]'::jsonb,
       '{}'::jsonb,
       false
WHERE NOT EXISTS (SELECT 1 FROM configuration_items WHERE fqdn = 'giftshop-storefront.demo');

INSERT INTO configuration_items (id, fqdn, ci_type, system_name, subsystem_name, software, tags, external_ids, auto_created)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000011',
       'giftshop-catalog.demo',
       'service',
       'Gift Shop',
       'Catalog',
       'giftshop-catalog',
       '["giftshop"]'::jsonb,
       '{}'::jsonb,
       false
WHERE NOT EXISTS (SELECT 1 FROM configuration_items WHERE fqdn = 'giftshop-catalog.demo');

INSERT INTO configuration_items (id, fqdn, ci_type, system_name, subsystem_name, software, tags, external_ids, auto_created)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000012',
       'giftshop-checkout.demo',
       'service',
       'Gift Shop',
       'Checkout',
       'giftshop-checkout',
       '["giftshop"]'::jsonb,
       '{}'::jsonb,
       false
WHERE NOT EXISTS (SELECT 1 FROM configuration_items WHERE fqdn = 'giftshop-checkout.demo');

INSERT INTO configuration_items (id, fqdn, ci_type, system_name, subsystem_name, software, tags, external_ids, auto_created)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000013',
       'giftshop-postgres.demo',
       'database',
       'Gift Shop',
       'Postgres',
       'postgres',
       '["giftshop"]'::jsonb,
       '{}'::jsonb,
       false
WHERE NOT EXISTS (SELECT 1 FROM configuration_items WHERE fqdn = 'giftshop-postgres.demo');

INSERT INTO product_ci (product_id, ci_id)
SELECT p.id, c.id
FROM products p
JOIN configuration_items c ON c.fqdn IN (
    'giftshop-storefront.demo',
    'giftshop-catalog.demo',
    'giftshop-checkout.demo',
    'giftshop-postgres.demo'
)
WHERE p.code = 'giftshop'
ON CONFLICT (product_id, ci_id) DO NOTHING;

INSERT INTO product_component (id, product_id, code, name, weight, influence_type, critical_threshold, sort_order)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000030',
       p.id,
       'POWER',
       'POWER',
       20,
       'weighted',
       100,
       1
FROM products p
WHERE p.code = 'giftshop'
ON CONFLICT (product_id, code) DO UPDATE
SET name = EXCLUDED.name,
    weight = EXCLUDED.weight,
    influence_type = EXCLUDED.influence_type,
    critical_threshold = EXCLUDED.critical_threshold,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

INSERT INTO product_component (id, product_id, code, name, weight, influence_type, critical_threshold, sort_order)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000031',
       p.id,
       'CPU',
       'CPU',
       25,
       'weighted',
       100,
       2
FROM products p
WHERE p.code = 'giftshop'
ON CONFLICT (product_id, code) DO UPDATE
SET name = EXCLUDED.name,
    weight = EXCLUDED.weight,
    influence_type = EXCLUDED.influence_type,
    critical_threshold = EXCLUDED.critical_threshold,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

INSERT INTO product_component (id, product_id, code, name, weight, influence_type, critical_threshold, sort_order)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000032',
       p.id,
       'HDD',
       'HDD',
       15,
       'weighted',
       100,
       3
FROM products p
WHERE p.code = 'giftshop'
ON CONFLICT (product_id, code) DO UPDATE
SET name = EXCLUDED.name,
    weight = EXCLUDED.weight,
    influence_type = EXCLUDED.influence_type,
    critical_threshold = EXCLUDED.critical_threshold,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

INSERT INTO product_component (id, product_id, code, name, weight, influence_type, critical_threshold, sort_order)
SELECT 'a0e1d2c3-b4a5-4678-89ab-000000000033',
       p.id,
       'AVAILABILITY',
       'AVAILABILITY',
       40,
       'critical',
       100,
       4
FROM products p
WHERE p.code = 'giftshop'
ON CONFLICT (product_id, code) DO UPDATE
SET name = EXCLUDED.name,
    weight = EXCLUDED.weight,
    influence_type = EXCLUDED.influence_type,
    critical_threshold = EXCLUDED.critical_threshold,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

-- If Liquibase 013 (or product create) added COMMON, keep it from dominating the ratio
-- and do not leave the same CI on COMMON plus another slot.
UPDATE product_component pc
SET weight = 0, updated_at = NOW()
FROM products p
WHERE pc.product_id = p.id
  AND p.code = 'giftshop'
  AND pc.code = 'COMMON';

DELETE FROM product_component_ci pcc
USING product_component pc, products p
WHERE pcc.component_id = pc.id
  AND pc.product_id = p.id
  AND p.code = 'giftshop'
  AND pc.code = 'COMMON';

INSERT INTO product_component_ci (component_id, ci_id)
SELECT pc.id, ci.id
FROM products p
JOIN product_component pc ON pc.product_id = p.id AND pc.code = 'POWER'
JOIN configuration_items ci ON ci.fqdn = 'giftshop-storefront.demo'
WHERE p.code = 'giftshop'
ON CONFLICT (component_id, ci_id) DO NOTHING;

INSERT INTO product_component_ci (component_id, ci_id)
SELECT pc.id, ci.id
FROM products p
JOIN product_component pc ON pc.product_id = p.id AND pc.code = 'CPU'
JOIN configuration_items ci ON ci.fqdn = 'giftshop-catalog.demo'
WHERE p.code = 'giftshop'
ON CONFLICT (component_id, ci_id) DO NOTHING;

INSERT INTO product_component_ci (component_id, ci_id)
SELECT pc.id, ci.id
FROM products p
JOIN product_component pc ON pc.product_id = p.id AND pc.code = 'HDD'
JOIN configuration_items ci ON ci.fqdn = 'giftshop-postgres.demo'
WHERE p.code = 'giftshop'
ON CONFLICT (component_id, ci_id) DO NOTHING;

INSERT INTO product_component_ci (component_id, ci_id)
SELECT pc.id, ci.id
FROM products p
JOIN product_component pc ON pc.product_id = p.id AND pc.code = 'AVAILABILITY'
JOIN configuration_items ci ON ci.fqdn = 'giftshop-checkout.demo'
WHERE p.code = 'giftshop'
ON CONFLICT (component_id, ci_id) DO NOTHING;

INSERT INTO event_sources (
    id,
    name,
    type,
    protocol,
    endpoint,
    api_key_hash,
    api_key_prefix,
    status,
    schedule,
    filter_rules,
    parser_config,
    webhook_path_key
)
SELECT
    'a0e1d2c3-b4a5-4678-89ab-000000000020',
    'Gift Shop Prometheus pull',
    'pull_etl',
    'HTTP/Prometheus',
    'http://giftshop-catalog:8092/metrics',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'giftshop-pull',
    'active',
    '30s',
    '{"enabled": true}'::jsonb,
    '{
      "targets": [
        {"url": "http://giftshop-storefront:8091/metrics", "ciFqdn": "giftshop-storefront.demo"},
        {"url": "http://giftshop-catalog:8092/metrics", "ciFqdn": "giftshop-catalog.demo"},
        {"url": "http://giftshop-checkout:8093/metrics", "ciFqdn": "giftshop-checkout.demo"}
      ],
      "rules": [
        {"metric": "up", "thresholds": {"critical": 0}, "invert": true},
        {"metric": "process_cpu_usage", "thresholds": {"warning": 0.70, "major": 0.85, "critical": 0.95}},
        {"metric": "process_disk_usage", "thresholds": {"warning": 0.80, "major": 0.90, "critical": 0.95}}
      ]
    }'::jsonb,
    'giftshop-metrics'
WHERE NOT EXISTS (SELECT 1 FROM event_sources WHERE webhook_path_key = 'giftshop-metrics');

UPDATE event_sources
SET type = 'pull_etl',
    protocol = 'HTTP/Prometheus',
    endpoint = 'http://giftshop-catalog:8092/metrics',
    status = 'active',
    schedule = '30s',
    parser_config = '{
      "targets": [
        {"url": "http://giftshop-storefront:8091/metrics", "ciFqdn": "giftshop-storefront.demo"},
        {"url": "http://giftshop-catalog:8092/metrics", "ciFqdn": "giftshop-catalog.demo"},
        {"url": "http://giftshop-checkout:8093/metrics", "ciFqdn": "giftshop-checkout.demo"}
      ],
      "rules": [
        {"metric": "up", "thresholds": {"critical": 0}, "invert": true},
        {"metric": "process_cpu_usage", "thresholds": {"warning": 0.70, "major": 0.85, "critical": 0.95}},
        {"metric": "process_disk_usage", "thresholds": {"warning": 0.80, "major": 0.90, "critical": 0.95}}
      ]
    }'::jsonb,
    updated_at = NOW()
WHERE webhook_path_key = 'giftshop-metrics';

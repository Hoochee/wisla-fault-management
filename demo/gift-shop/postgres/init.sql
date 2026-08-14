CREATE TABLE IF NOT EXISTS catalog_items (
    id           SERIAL PRIMARY KEY,
    sku          VARCHAR(32) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    description  TEXT NOT NULL,
    price_cents  INT NOT NULL,
    emoji        VARCHAR(16) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id             SERIAL PRIMARY KEY,
    customer_name  VARCHAR(128) NOT NULL,
    items          JSONB NOT NULL,
    total_cents    INT NOT NULL,
    status         VARCHAR(32) NOT NULL,
    payment_ref    VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO catalog_items (sku, name, description, price_cents, emoji) VALUES
    ('mug-star', 'Starry Mug', 'Ceramic mug with a night-sky glaze.', 1290, '☕'),
    ('bear-honey', 'Honey Bear', 'Soft bear with a tiny honey pot.', 2490, '🧸'),
    ('candle-pine', 'Pine Candle', 'Soy wax candle, forest scent.', 1590, '🕯️'),
    ('scarf-wool', 'Wool Scarf', 'Hand-knit merino scarf.', 3290, '🧣'),
    ('box-assorted', 'Assorted Box', 'A little of everything.', 4990, '🎁')
ON CONFLICT (sku) DO NOTHING;

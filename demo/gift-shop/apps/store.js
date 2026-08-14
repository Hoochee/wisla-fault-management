import pg from "pg";

const FALLBACK_ITEMS = [
    { id: 1, sku: "mug-star", name: "Starry Mug", description: "Ceramic mug with a night-sky glaze.", price_cents: 1290, emoji: "☕" },
    { id: 2, sku: "bear-honey", name: "Honey Bear", description: "Soft bear with a tiny honey pot.", price_cents: 2490, emoji: "🧸" },
    { id: 3, sku: "candle-pine", name: "Pine Candle", description: "Soy wax candle, forest scent.", price_cents: 1590, emoji: "🕯️" },
    { id: 4, sku: "scarf-wool", name: "Wool Scarf", description: "Hand-knit merino scarf.", price_cents: 3290, emoji: "🧣" },
    { id: 5, sku: "box-assorted", name: "Assorted Box", description: "A little of everything.", price_cents: 4990, emoji: "🎁" },
];

export function createStore(databaseUrl) {
    const pool = databaseUrl ? new pg.Pool({ connectionString: databaseUrl }) : null;

    async function listProducts() {
        if (!pool) {
            return FALLBACK_ITEMS;
        }
        try {
            const result = await pool.query(
                "SELECT id, sku, name, description, price_cents, emoji FROM catalog_items ORDER BY id"
            );
            return result.rows.length > 0 ? result.rows : FALLBACK_ITEMS;
        } catch {
            return FALLBACK_ITEMS;
        }
    }

    async function insertOrder(customerName, items, totalCents, paymentRef) {
        if (!pool) {
            return { id: Date.now() % 1_000_000 };
        }
        try {
            const result = await pool.query(
                `INSERT INTO orders (customer_name, items, total_cents, status, payment_ref)
                 VALUES ($1, $2::jsonb, $3, 'paid', $4)
                 RETURNING id`,
                [customerName, JSON.stringify(items), totalCents, paymentRef]
            );
            return result.rows[0];
        } catch {
            return { id: Date.now() % 1_000_000 };
        }
    }

    return { listProducts, insertOrder };
}

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createChaos } from "./chaos.js";
import { createMetrics } from "./metrics.js";
import { createStore } from "./store.js";

const ROLE = process.env.SERVICE_ROLE || "catalog";
const PORT = Number(process.env.PORT || defaultPort(ROLE));
const CATALOG_URL = process.env.CATALOG_URL || "http://giftshop-catalog:8092";
const CHECKOUT_URL = process.env.CHECKOUT_URL || "http://giftshop-checkout:8093";
const DATABASE_URL = process.env.DATABASE_URL || "";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, "public");

const chaos = createChaos();
const metrics = createMetrics(`giftshop-${ROLE}`, chaos);
const store = createStore(ROLE === "storefront" ? "" : DATABASE_URL);

const CHAOS_KINDS = new Set(["cpu", "latency", "errors", "disk", "down", "reset"]);

const server = http.createServer(async (req, res) => {
    try {
        await handle(req, res);
    } catch (err) {
        console.error(err);
        json(res, 500, { error: "internal" });
    }
});

server.listen(PORT, "0.0.0.0", () => {
    console.log(`giftshop-${ROLE} listening on ${PORT}`);
});

async function handle(req, res) {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    const method = req.method || "GET";

    if (url.pathname === "/health" && method === "GET") {
        json(res, 200, { status: "UP", role: ROLE, chaos: chaos.snapshot() });
        return;
    }

    if (url.pathname === "/metrics" && method === "GET") {
        text(res, 200, metrics.render(), "text/plain; version=0.0.4; charset=utf-8");
        return;
    }

    if (method === "POST" && url.pathname.startsWith("/chaos/")) {
        const kind = url.pathname.slice("/chaos/".length);
        if (!CHAOS_KINDS.has(kind)) {
            json(res, 404, { error: "unknown chaos kind" });
            return;
        }
        const body = await readJson(req);
        chaos.apply(kind, body);
        json(res, 202, { ok: true, kind, chaos: chaos.snapshot() });
        return;
    }

    if (ROLE === "storefront" && method === "POST" && url.pathname === "/api/chaos") {
        await handleChaosProxy(req, res);
        return;
    }

    if (chaos.isDown() && url.pathname !== "/metrics" && url.pathname !== "/health") {
        json(res, 503, { error: "chaos down" });
        return;
    }

    const snap = chaos.snapshot();
    if (snap.latencyMs > 0) {
        await delay(snap.latencyMs);
    }

    if (shouldError(snap) && isApiPath(url.pathname)) {
        metrics.recordError();
        json(res, 500, { error: "chaos errors" });
        return;
    }

    if (ROLE === "catalog") {
        await handleCatalog(url, method, res);
        return;
    }
    if (ROLE === "checkout") {
        await handleCheckout(req, url, method, res);
        return;
    }
    await handleStorefront(req, url, method, res);
}

async function handleCatalog(url, method, res) {
    if (method === "GET" && (url.pathname === "/" || url.pathname === "/api")) {
        json(res, 200, { service: "giftshop-catalog", products: "/api/products" });
        return;
    }
    if (method === "GET" && url.pathname === "/api/products") {
        json(res, 200, { products: await store.listProducts() });
        return;
    }
    json(res, 404, { error: "not found" });
}

async function handleCheckout(req, url, method, res) {
    if (method === "GET" && (url.pathname === "/" || url.pathname === "/api")) {
        json(res, 200, { service: "giftshop-checkout", checkout: "POST /api/checkout" });
        return;
    }
    if (method === "POST" && url.pathname === "/api/checkout") {
        const body = await readJson(req);
        const items = Array.isArray(body.items) ? body.items : [];
        const catalog = await store.listProducts();
        const bySku = new Map(catalog.map((p) => [p.sku, p]));
        let total = 0;
        const resolved = [];
        for (const item of items) {
            const product = bySku.get(item.sku);
            const qty = Math.max(1, Number(item.qty) || 1);
            if (!product) {
                json(res, 400, { error: `unknown sku ${item.sku}` });
                return;
            }
            total += product.price_cents * qty;
            resolved.push({ sku: product.sku, name: product.name, qty, price_cents: product.price_cents });
        }
        const paymentRef = `mock-pay-${Date.now().toString(36)}`;
        const customerName = String(body.customerName || "Guest").slice(0, 128);
        const row = await store.insertOrder(customerName, resolved, total, paymentRef);
        json(res, 201, {
            orderId: row.id,
            status: "paid",
            paymentRef,
            totalCents: total,
            mock: true,
        });
        return;
    }
    json(res, 404, { error: "not found" });
}

async function handleStorefront(req, url, method, res) {
    if (method === "GET" && url.pathname === "/api/products") {
        await proxy(res, `${CATALOG_URL}/api/products`);
        return;
    }
    if (method === "POST" && url.pathname === "/api/checkout") {
        const body = await readRaw(req);
        await proxy(res, `${CHECKOUT_URL}/api/checkout`, { method: "POST", body, headers: { "content-type": "application/json" } });
        return;
    }
    if (method === "POST" && url.pathname === "/api/chaos") {
        await handleChaosProxy(req, res);
        return;
    }
    if (method !== "GET") {
        json(res, 405, { error: "method not allowed" });
        return;
    }
    const relative = url.pathname === "/" ? "index.html" : url.pathname.replace(/^\/+/, "");
    const filePath = path.normalize(path.join(publicDir, relative));
    if (!filePath.startsWith(publicDir)) {
        json(res, 403, { error: "forbidden" });
        return;
    }
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        json(res, 404, { error: "not found" });
        return;
    }
    const ext = path.extname(filePath);
    const type = ext === ".css" ? "text/css" : ext === ".js" ? "text/javascript" : "text/html; charset=utf-8";
    text(res, 200, fs.readFileSync(filePath), type);
}

async function handleChaosProxy(req, res) {
    const body = await readJson(req);
    const kind = String(body.kind || "");
    if (!CHAOS_KINDS.has(kind)) {
        json(res, 400, { error: "unknown chaos kind" });
        return;
    }
    const payload = {
        durationSeconds: body.durationSeconds ?? 120,
        value: body.value,
        latencyMs: body.latencyMs,
    };
    const target = String(body.target || "checkout");
    const targets =
        target === "all"
            ? ["storefront", "catalog", "checkout"]
            : [target];
    const allowed = new Set(["storefront", "catalog", "checkout"]);
    const results = [];
    for (const name of targets) {
        if (!allowed.has(name)) {
            json(res, 400, { error: `unknown target ${name}` });
            return;
        }
        results.push(await applyChaosOn(name, kind, payload));
    }
    json(res, 202, { ok: true, kind, results });
}

async function applyChaosOn(name, kind, payload) {
    if (name === "storefront") {
        chaos.apply(kind, payload);
        return { target: name, ok: true, chaos: chaos.snapshot() };
    }
    const base = name === "catalog" ? CATALOG_URL : CHECKOUT_URL;
    const upstream = await fetch(`${base}/chaos/${kind}`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
    });
    const data = await upstream.json().catch(() => ({}));
    return { target: name, status: upstream.status, ...data };
}

async function proxy(res, target, init = {}) {
    const upstream = await fetch(target, init);
    const buf = Buffer.from(await upstream.arrayBuffer());
    res.writeHead(upstream.status, {
        "content-type": upstream.headers.get("content-type") || "application/json",
        "access-control-allow-origin": "*",
    });
    res.end(buf);
}

function shouldError(snap) {
    return snap.errorRate > 0 && Math.random() < snap.errorRate;
}

function isApiPath(pathname) {
    return pathname.startsWith("/api");
}

function defaultPort(role) {
    if (role === "storefront") {
        return "8091";
    }
    if (role === "checkout") {
        return "8093";
    }
    return "8092";
}

function json(res, status, body) {
    const payload = JSON.stringify(body);
    res.writeHead(status, {
        "content-type": "application/json; charset=utf-8",
        "access-control-allow-origin": "*",
        "content-length": Buffer.byteLength(payload),
    });
    res.end(payload);
}

function text(res, status, body, contentType) {
    res.writeHead(status, {
        "content-type": contentType,
        "access-control-allow-origin": "*",
    });
    res.end(body);
}

async function readJson(req) {
    const raw = await readRaw(req);
    if (!raw) {
        return {};
    }
    try {
        return JSON.parse(raw.toString("utf8"));
    } catch {
        return {};
    }
}

async function readRaw(req) {
    const chunks = [];
    for await (const chunk of req) {
        chunks.push(chunk);
    }
    return chunks.length ? Buffer.concat(chunks) : Buffer.alloc(0);
}

function delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

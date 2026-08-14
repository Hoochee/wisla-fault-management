const catalogEl = document.getElementById("catalog");
const cartEl = document.getElementById("cart");
const cartItemsEl = document.getElementById("cart-items");
const cartCountEl = document.getElementById("cart-count");
const cartTotalEl = document.getElementById("cart-total");
const resultEl = document.getElementById("checkout-result");

const cart = new Map();

document.getElementById("cart-toggle").addEventListener("click", () => {
  cartEl.hidden = !cartEl.hidden;
});

document.getElementById("checkout-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const customerName = new FormData(event.target).get("customerName");
  const items = [...cart.values()].map(({ product, qty }) => ({ sku: product.sku, qty }));
  if (items.length === 0) {
    resultEl.textContent = "Cart is empty.";
    return;
  }
  resultEl.textContent = "Paying…";
  try {
    const res = await fetch("/api/checkout", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ customerName, items }),
    });
    const body = await res.json();
    if (!res.ok) {
      resultEl.textContent = body.error || "Checkout failed";
      return;
    }
    cart.clear();
    renderCart();
    resultEl.textContent = `Paid (mock) — order ${body.orderId}, ref ${body.paymentRef}`;
  } catch (err) {
    resultEl.textContent = String(err);
  }
});

document.querySelectorAll("[data-chaos]").forEach((button) => {
  button.addEventListener("click", () => runChaos(button));
});

loadCatalog();

async function runChaos(button) {
  const status = document.getElementById("chaos-status");
  const kind = button.dataset.kind;
  const payload = {
    target: button.dataset.target,
    kind,
    durationSeconds: 120,
  };
  if (button.dataset.value) {
    payload.value = Number(button.dataset.value);
  }
  if (button.dataset.latency) {
    payload.latencyMs = Number(button.dataset.latency);
  }
  status.textContent = `Sending ${kind} → ${payload.target}…`;
  try {
    const res = await fetch("/api/chaos", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
    });
    const body = await res.json();
    if (!res.ok) {
      status.textContent = body.error || "Chaos failed";
      return;
    }
    status.textContent =
      kind === "reset"
        ? "Reset sent. Wait up to 30s for FM to clear."
        : `${kind} on ${payload.target} for 120s. Refresh FM /health in ~30s.`;
  } catch (err) {
    status.textContent = String(err);
  }
}

async function loadCatalog() {
  const res = await fetch("/api/products");
  const body = await res.json();
  catalogEl.innerHTML = "";
  for (const product of body.products || []) {
    const card = document.createElement("article");
    card.className = "card";
    card.innerHTML = `
      <div class="emoji">${product.emoji}</div>
      <h3>${escapeHtml(product.name)}</h3>
      <p>${escapeHtml(product.description)}</p>
      <p class="price">${(product.price_cents / 100).toFixed(2)}</p>
    `;
    const add = document.createElement("button");
    add.type = "button";
    add.textContent = "Add to cart";
    add.addEventListener("click", () => {
      const current = cart.get(product.sku) || { product, qty: 0 };
      current.qty += 1;
      cart.set(product.sku, current);
      renderCart();
      cartEl.hidden = false;
    });
    card.appendChild(add);
    catalogEl.appendChild(card);
  }
}

function renderCart() {
  cartItemsEl.innerHTML = "";
  let total = 0;
  let count = 0;
  for (const { product, qty } of cart.values()) {
    total += product.price_cents * qty;
    count += qty;
    const li = document.createElement("li");
    li.textContent = `${product.emoji} ${product.name} × ${qty}`;
    cartItemsEl.appendChild(li);
  }
  cartCountEl.textContent = String(count);
  cartTotalEl.textContent = count ? `Total ${(total / 100).toFixed(2)}` : "Cart is empty";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

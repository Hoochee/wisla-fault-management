import { test, expect, loginAsAdmin } from './fixtures/auth';
import type { Page, Response } from '@playwright/test';

function uniqueSuffix(): string {
  return Date.now().toString(36);
}

async function dismissToasts(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelectorAll('app-push-toast').forEach((el) => el.remove());
  });
}

function isProductsListResponse(response: Response): boolean {
  if (response.request().method() !== 'GET') return false;
  let pathname: string;
  try {
    pathname = new URL(response.url()).pathname.replace(/\/$/, '');
  } catch {
    return false;
  }
  return pathname === '/api/v1/health/products';
}

async function gotoHealth(page: Page): Promise<void> {
  // Backend serves GET /health as JSON; reach the SPA route via client-side navigation.
  await page.goto('/');
  await page.locator('a.nav-child[href="/health"]').click();
  await expect(page).toHaveURL(/\/health$/);
  await expect(page.locator('app-health .heatmap-section')).toBeVisible({ timeout: 15000 });
}

function heatCellForProduct(page: Page, productName: string) {
  return page.locator('.heat-cell-wrap').filter({
    has: page.locator('.heat-cell .name', { hasText: productName }),
  });
}

async function openCreateProductModal(page: Page): Promise<void> {
  await dismissToasts(page);
  await page.getByRole('button', { name: '+ \u041f\u0440\u043e\u0434\u0443\u043a\u0442' }).click();
  await expect(page.getByRole('heading', { name: '\u041d\u043e\u0432\u044b\u0439 \u043f\u0440\u043e\u0434\u0443\u043a\u0442' })).toBeVisible();
  await expect(page.locator('.ci-list .ci-option').first()).toBeVisible({ timeout: 15000 });
}

async function createProductWithCi(
  page: Page,
  opts: { name: string; code: string; bindCiFqdn: string },
): Promise<void> {
  await openCreateProductModal(page);
  await page.locator('input[name="name"]').fill(opts.name);
  await page.locator('input[name="code"]').fill(opts.code);
  await page.locator('input[name="tenant"]').fill('e2e-tenant');
  await page.locator('input[name="site"]').fill('e2e-site');
  const ciOption = page.locator('.ci-option').filter({ hasText: opts.bindCiFqdn });
  await expect(ciOption).toBeVisible();
  await ciOption.locator('input[type="checkbox"]').check();
  await page.getByRole('button', { name: '\u0421\u043e\u0437\u0434\u0430\u0442\u044c' }).click();
  await expect(page.getByRole('heading', { name: '\u041d\u043e\u0432\u044b\u0439 \u043f\u0440\u043e\u0434\u0443\u043a\u0442' })).not.toBeVisible({
    timeout: 15000,
  });
  await dismissToasts(page);
  await expect(heatCellForProduct(page, opts.name)).toBeVisible({ timeout: 15000 });
}

async function openProductPage(page: Page, productName: string): Promise<void> {
  await heatCellForProduct(page, productName).locator('.heat-cell').click();
  await dismissToasts(page);
  await page.locator('.footer-link a').click();
  await expect(page).toHaveURL(/\/health\/[0-9a-f-]+$/);
  await expect(page.locator('app-health-product h1')).toHaveText(productName, { timeout: 15000 });
}

test.describe('Product health graph', () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await dismissToasts(page);
  });

  test('authenticated /health shows healthPercent from API, not client-fake CPU/HDD', async ({
    page,
  }) => {
    const listPromise = page.waitForResponse(isProductsListResponse, { timeout: 20000 });
    await gotoHealth(page);
    const listResponse = await listPromise;
    expect(listResponse.ok()).toBeTruthy();
    let products = (await listResponse.json()) as Array<{
      name: string;
      healthPercent?: number | null;
      components?: Array<{ code?: string; name?: string }>;
    }>;

    if (products.length === 0) {
      const suffix = uniqueSuffix();
      const reloadPromise = page.waitForResponse(isProductsListResponse, { timeout: 20000 });
      await createProductWithCi(page, {
        name: `E2E HealthPct ${suffix}`,
        code: `e2e-hp-${suffix}`,
        bindCiFqdn: 'demo-server.wisla.local',
      });
      products = (await (await reloadPromise).json()) as typeof products;
    }

    expect(products.length).toBeGreaterThan(0);

    await expect(page.getByText('\u0426\u0432\u0435\u0442 = healthPercent:')).toBeVisible();

    for (const product of products) {
      const cell = heatCellForProduct(page, product.name);
      await expect(cell).toBeVisible();
      const expected =
        product.healthPercent == null ? '\u2014%' : `${product.healthPercent}%`;
      await expect(cell.locator('.pct')).toHaveText(expected);
    }

    const apiNames = new Set(products.map((p) => p.name));
    for (const fake of ['CPU', 'HDD']) {
      if (!apiNames.has(fake)) {
        await expect(page.locator('.heat-cell .name', { hasText: fake })).toHaveCount(0);
      }
    }

    const first = products[0];
    await heatCellForProduct(page, first.name).locator('.heat-cell').click();

    const apiComponentLabels = new Set<string>();
    for (const row of products) {
      for (const c of row.components ?? []) {
        if (c.code) apiComponentLabels.add(c.code);
        if (c.name) apiComponentLabels.add(c.name);
      }
    }

    const ciHealth = page.locator('app-ci-health-tab .components');
    if ((await ciHealth.count()) > 0 && (await ciHealth.first().isVisible())) {
      for (const fake of ['CPU', 'HDD']) {
        if (!apiComponentLabels.has(fake)) {
          await expect(ciHealth.first()).not.toContainText(fake);
        }
      }
    }
  });

  test('admin PATCHes component weight on /health/:productId and sees it after reload', async ({
    page,
  }) => {
    const suffix = uniqueSuffix();
    const productName = `E2E Weight ${suffix}`;
    const productCode = `e2e-wt-${suffix}`;

    await gotoHealth(page);
    await createProductWithCi(page, {
      name: productName,
      code: productCode,
      bindCiFqdn: 'demo-server.wisla.local',
    });
    await openProductPage(page, productName);

    const editor = page.locator('app-component-weight-editor');
    await expect(editor).toBeVisible({ timeout: 15000 });
    const weightInput = editor.locator('input[type="number"]').first();
    await expect(weightInput).toBeVisible({ timeout: 15000 });

    const current = Number(await weightInput.inputValue());
    const nextWeight = current === 42 ? 37 : 42;
    await weightInput.fill(String(nextWeight));

    const patchPromise = page.waitForResponse((r) => {
      return (
        r.request().method() === 'PATCH' &&
        r.url().includes('/api/v1/admin/products/') &&
        r.ok()
      );
    }, { timeout: 20000 });
    await editor.getByRole('button', { name: '\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c' }).click();
    const patchResp = await patchPromise;
    expect(patchResp.ok()).toBeTruthy();
    await dismissToasts(page);

    // Reload via SPA shell: GET /health is actuator JSON, so do not page.goto('/health').
    await page.locator('a.back').click();
    await expect(page).toHaveURL(/\/health$/);
    await expect(heatCellForProduct(page, productName)).toBeVisible({ timeout: 15000 });
    await openProductPage(page, productName);

    const editorAfter = page.locator('app-component-weight-editor');
    await expect(editorAfter.locator('input[type="number"]').first()).toHaveValue(String(nextWeight), {
      timeout: 15000,
    });
  });

  test.skip('optional: chaos on checkout drops giftshop healthPercent', async () => {
    // Overlay not running; skip without blocking the suite.
  });
});

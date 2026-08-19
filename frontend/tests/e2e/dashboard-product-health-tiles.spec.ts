import { test, expect, loginAsAdmin } from './fixtures/auth';
import type { Page, Response } from '@playwright/test';

type HealthListRow = {
  id: string;
  name: string;
  healthPercent?: number | null;
};

const GIFT_SHOP_FQDNS = [
  'giftshop-storefront.demo',
  'giftshop-catalog.demo',
  'giftshop-checkout.demo',
  'giftshop-postgres.demo',
];

const SLOT_CODES = ['POWER', 'CPU', 'HDD', 'AVAILABILITY'];

function isProductsListResponse(response: Response): boolean {
  if (response.request().method() !== 'GET' || !response.ok()) return false;
  let pathname: string;
  try {
    pathname = new URL(response.url()).pathname.replace(/\/$/, '');
  } catch {
    return false;
  }
  return pathname === '/api/v1/health/products';
}

async function dismissToasts(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelectorAll('app-push-toast').forEach((el) => el.remove());
  });
}

function productTile(page: Page, productName: string) {
  return page.locator('a.product-cell').filter({ has: page.locator('.product-name', { hasText: productName }) });
}

function percentLabel(value: number | null | undefined): string {
  return value == null ? '—%' : `${value}%`;
}

function pickProduct(products: HealthListRow[]): HealthListRow | undefined {
  return products.find((p) => /gift shop/i.test(p.name)) ?? products[0];
}

async function expectMapsAboveHealth(page: Page): Promise<void> {
  const mapsHeading = page.locator('app-dashboard h2').filter({ hasText: 'Карты событий' });
  const healthHeading = page.locator('app-dashboard h2').filter({ hasText: 'Здоровье продуктов' });
  await expect(mapsHeading).toBeVisible({ timeout: 15000 });
  await expect(healthHeading).toBeVisible({ timeout: 15000 });
  const mapsBox = await mapsHeading.boundingBox();
  const healthBox = await healthHeading.boundingBox();
  expect(mapsBox).toBeTruthy();
  expect(healthBox).toBeTruthy();
  expect(mapsBox!.y).toBeLessThan(healthBox!.y);
}

test.describe('Dashboard product health tiles', () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await dismissToasts(page);
  });

  test('tiles sit below maps, show Gift Shop CIs, and open the product graph', async ({ page }) => {
    const listPromise = page.waitForResponse(isProductsListResponse, { timeout: 20000 });
    await page.reload();
    const listResponse = await listPromise;
    expect(listResponse.ok()).toBeTruthy();
    const products = (await listResponse.json()) as HealthListRow[];

    await expectMapsAboveHealth(page);

    const healthHeading = page.locator('app-dashboard h2').filter({ hasText: 'Здоровье продуктов' });
    const headerLink = healthHeading.locator('a');
    await expect(headerLink).toHaveAttribute('href', '/health');

    if (products.length === 0) {
      await expect(page.locator('a.product-cell')).toHaveCount(0);
      return;
    }

    const product = pickProduct(products)!;
    const tile = productTile(page, product.name);
    await expect(tile).toBeVisible();
    await expect(tile.locator('.health-score')).toHaveText(percentLabel(product.healthPercent));
    await expect(tile.locator('.damage-box')).toContainText('Анализ урона');
    await expect(tile.locator('.ci-table th', { hasText: 'Название КЕ' })).toBeVisible();
    await expect(tile.locator('.ci-table th', { hasText: 'Тип' })).toBeVisible();
    await expect(tile.locator('.ci-table th', { hasText: 'Здоровье' })).toBeVisible();
    await expect(tile).toHaveAttribute('href', `/health/${product.id}`);

    if (/gift shop/i.test(product.name)) {
      for (const fqdn of GIFT_SHOP_FQDNS) {
        await expect(tile.locator('.product-ci').filter({ hasText: fqdn })).toBeVisible({ timeout: 15000 });
      }
      await expect(tile.locator('.product-ci')).toHaveCount(4);
      await expect(tile.locator('.product-ci').filter({ hasText: 'giftshop-postgres.demo' })).toContainText(
        /database/i,
      );
      await expect(tile.locator('.product-ci').filter({ hasText: 'giftshop-storefront.demo' })).toContainText(
        /service/i,
      );
    }

    for (const code of SLOT_CODES) {
      await expect(tile.locator('.ci-label', { hasText: new RegExp(`^${code}$`) })).toHaveCount(0);
    }

    await headerLink.click();
    await expect(page).toHaveURL(/\/health$/);
    await expect(page.locator('app-health .heatmap-section')).toBeVisible({ timeout: 15000 });

    await page.goto('/');
    await expect(productTile(page, product.name)).toBeVisible({ timeout: 15000 });
    await productTile(page, product.name).click();
    await expect(page).toHaveURL(new RegExp(`/health/${product.id}$`));
  });

  test('priority counters and event maps stay on the dashboard after tiles render', async ({ page }) => {
    await page.goto('/');
    await expectMapsAboveHealth(page);

    for (const label of ['Critical', 'Major', 'Minor', 'Warning']) {
      await expect(page.locator('a.severity-card').filter({ hasText: label })).toBeVisible();
    }
    await expect(page.locator('a.severity-card')).toHaveCount(4);
    await expect(page.locator('a.map-item').first()).toBeVisible();
    await expect(page.locator('app-dashboard p.total')).toContainText('Всего продуктов');
    await expect(page.locator('app-dashboard p.total')).not.toContainText('Всего активных');
  });
});

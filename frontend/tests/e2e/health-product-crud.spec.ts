import { test, expect, loginAsAdmin } from './fixtures/auth';
import type { Page } from '@playwright/test';

function uniqueSuffix(): string {
  return Date.now().toString(36);
}

async function dismissToasts(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelectorAll('app-push-toast').forEach((el) => el.remove());
  });
}

async function gotoHealth(page: Page): Promise<void> {
  // Backend serves GET /health as JSON; reach the SPA route via client-side navigation.
  await page.goto('/');
  await page.locator('a.nav-child[href="/health"]').click();
  await expect(page).toHaveURL(/\/health$/);
  await expect(page.locator('app-health .heatmap-section')).toBeVisible({ timeout: 15000 });
  await expect(page.getByRole('button', { name: '+ Продукт' })).toBeVisible();
}

async function openCreateProductModal(page: Page): Promise<void> {
  await dismissToasts(page);
  await page.getByRole('button', { name: '+ Продукт' }).click();
  await expect(page.getByRole('heading', { name: 'Новый продукт' })).toBeVisible();
  await expect(page.locator('.ci-list .ci-option').first()).toBeVisible({ timeout: 15000 });
}

async function fillProductForm(
  page: Page,
  opts: { name: string; code: string; tenant?: string; site?: string; bindCiFqdn?: string },
): Promise<void> {
  await page.locator('input[name="name"]').fill(opts.name);
  await page.locator('input[name="code"]').fill(opts.code);
  await page.locator('input[name="tenant"]').fill(opts.tenant ?? 'e2e-tenant');
  await page.locator('input[name="site"]').fill(opts.site ?? 'e2e-site');

  if (opts.bindCiFqdn) {
    const ciOption = page.locator('.ci-option').filter({ hasText: opts.bindCiFqdn });
    await expect(ciOption).toBeVisible();
    await ciOption.locator('input[type="checkbox"]').check();
  }
}

async function submitCreateProduct(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Создать' }).click();
  await expect(page.getByRole('heading', { name: 'Новый продукт' })).not.toBeVisible({ timeout: 15000 });
}

function heatCellForProduct(page: Page, productName: string) {
  return page.locator('.heat-cell-wrap').filter({ has: page.locator('.heat-cell .name', { hasText: productName }) });
}

async function deleteProductFromHeatmap(page: Page, productName: string): Promise<void> {
  await dismissToasts(page);
  const cell = heatCellForProduct(page, productName);
  await cell.locator('.icon-btn.danger').click({ force: true });
  await expect(page.getByRole('heading', { name: 'Удалить продукт?' })).toBeVisible();
  await page.locator('.modal-sm').getByRole('button', { name: 'Удалить' }).click();
}

async function unlinkAllCisOnProductPage(page: Page): Promise<void> {
  const unlinkButtons = page.getByRole('button', { name: 'Отвязать' });
  while ((await unlinkButtons.count()) > 0) {
    await dismissToasts(page);
    const before = await unlinkButtons.count();
    await unlinkButtons.first().click({ force: true });
    await expect(unlinkButtons).toHaveCount(before - 1, { timeout: 10000 });
  }
}

test.describe('Health product CRUD', () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await dismissToasts(page);
  });

  test('admin creates product on /health, binds CI, cell appears on heatmap', async ({ page }) => {
    const suffix = uniqueSuffix();
    const productName = `E2E Product ${suffix}`;
    const productCode = `e2e-prod-${suffix}`;

    await gotoHealth(page);
    await openCreateProductModal(page);
    await fillProductForm(page, {
      name: productName,
      code: productCode,
      bindCiFqdn: 'demo-server.wisla.local',
    });
    await submitCreateProduct(page);
    await dismissToasts(page);

    await expect(heatCellForProduct(page, productName)).toBeVisible({ timeout: 15000 });
    // New product is auto-selected after heatmap reload; bound CI appears in the operative panel.
    await expect(page.locator('app-ci-sidebar')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('app-ci-sidebar').getByText('demo-server.wisla.local')).toBeVisible();
  });

  test('delete blocked with linked CI; unlink then delete succeeds', async ({ page }) => {
    const suffix = uniqueSuffix();
    const productName = `E2E Delete ${suffix}`;
    const productCode = `e2e-del-${suffix}`;

    await gotoHealth(page);
    await openCreateProductModal(page);
    await fillProductForm(page, {
      name: productName,
      code: productCode,
      bindCiFqdn: 'demo-server.wisla.local',
    });
    await submitCreateProduct(page);
    await dismissToasts(page);
    await expect(heatCellForProduct(page, productName)).toBeVisible({ timeout: 15000 });

    await deleteProductFromHeatmap(page, productName);

    await expect(page.getByRole('heading', { name: 'Невозможно удалить' })).toBeVisible();
    await expect(page.getByText(/Сначала отвяжите КЕ/)).toBeVisible();
    await dismissToasts(page);
    await page.getByRole('link', { name: 'Открыть состав' }).click({ force: true });
    await expect(page).toHaveURL(/\/health\/[0-9a-f-]+$/);
    await expect(page.getByRole('heading', { name: 'Состав продукта' })).toBeVisible();

    await unlinkAllCisOnProductPage(page);
    await expect(page.getByText('Нет связанных КЕ')).toBeVisible({ timeout: 15000 });

    await dismissToasts(page);
    await page.getByRole('button', { name: 'Удалить продукт' }).click({ force: true });
    await page.locator('.modal-sm').getByRole('button', { name: 'Удалить' }).click();
    await expect(page).toHaveURL(/\/health$/, { timeout: 15000 });

    await expect(heatCellForProduct(page, productName)).not.toBeVisible();
  });
});

import { test, expect, loginAsAdmin } from './fixtures/auth';
import type { Page } from '@playwright/test';

function trackPageErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('pageerror', (err) => errors.push(err.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const text = msg.text();
      if (!/favicon\.ico/i.test(text)) errors.push(text);
    }
  });
  return errors;
}

function expectNoFatalErrors(errors: string[]) {
  const filtered = errors.filter(
    (e) => !/favicon|Failed to load resource.*404/i.test(e),
  );
  expect(filtered).toEqual([]);
}

test.describe('MVP route smoke', () => {
  test('/login loads', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /WISLA FM/i })).toBeVisible();
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('/ dashboard', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/');
    await expect(page.locator('h1').first()).toBeVisible();
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/console', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/console');
    await expect(page.locator('app-query-bar').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('app-event-histogram').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table, table').first()).toBeVisible({ timeout: 15000 });
    await expect(
      page.locator('table.data-table thead th').filter({ hasText: /\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0439 \u043f\u043e\u0432\u0442\u043e\u0440/ }),
    ).toBeVisible();
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });



  test('/console sortable column headers', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/console');
    await expect(page.locator('table.data-table thead th.sortable').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table thead th.sortable')).toHaveCount(8);

    const createdHeader = page.locator('table.data-table thead th.sortable').filter({
      hasText: /\u0421\u043e\u0437\u0434\u0430\u043d\u043e/,
    });
    await expect(createdHeader).toHaveClass(/sortable/);
    await expect(createdHeader).toHaveClass(/sorted/);
    await expect(createdHeader.locator('.sort-indicator')).toBeVisible();

    await page.evaluate(() => {
      document.querySelectorAll('app-push-toast button.toast-close').forEach((btn) => {
        (btn as HTMLButtonElement).click();
      });
    });
    const repeatsHeader = page.locator('table.data-table thead th.sortable').filter({
      hasText: /\u041f\u043e\u0432\u0442\u043e\u0440\u044b/,
    });
    await expect(repeatsHeader).toHaveClass(/sortable/);
    await repeatsHeader.evaluate((el) => (el as HTMLElement).click());
    await expect(page).toHaveURL(/\/console\/?$/);
    await expect(repeatsHeader).toHaveClass(/sorted/, { timeout: 15000 });
    await expect(repeatsHeader.locator('.sort-indicator')).toBeVisible();
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/events/raw', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/events/raw');
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('app-query-bar').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('app-event-histogram').first()).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/sources', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/sources');
    await expect(page.locator('app-sources')).toBeVisible();
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('app-page-header a.btn-primary[href="/sources/new"]')).toBeVisible();
    await expect(page.locator('section.runtime-card').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('section.runtime-card h2').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table').first()).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/sources/new create form', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/sources/new');
    await expect(page.locator('app-source-new')).toBeVisible();
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('form.form input[formcontrolname="name"]')).toBeVisible();
    await expect(page.locator('form.form select[formcontrolname="type"]')).toBeVisible();
    await expect(page.locator('form.form button[type="submit"]')).toBeVisible();
    await expect(page.locator('form.form a[routerlink="/sources"]')).toBeVisible();
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/rules', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/rules');
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('table.data-table').first()).toBeVisible({ timeout: 15000 });
    await expect(
      page.getByRole('button', {
        name: /\u0412\u043a\u043b\u044e\u0447\u0438\u0442\u044c|\u0412\u044b\u043a\u043b\u044e\u0447\u0438\u0442\u044c/,
      }).first(),
    ).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/health', async ({ page }) => {
    const errors = trackPageErrors(page);
    // Backend exposes GET /health (JSON); use client-side routing from the SPA shell.
    await page.goto('/');
    await page.locator('a.nav-child[href="/health"]').click();
    await expect(page).toHaveURL(/\/health$/);
    await expect(page.locator('app-health .heatmap-section').first()).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/admin/users', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/admin/users');
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('table.data-table').first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: /\+ \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c/ })).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/settings', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/settings');
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('section.card').first()).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/rules/new', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/rules/new');
    await expect(page.locator('app-page-header h1').first()).toBeVisible();
    await expect(page.locator('app-rule-builder').first()).toBeVisible({ timeout: 15000 });
    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });

  test('/rules/new palette notify and push blocks', async ({ page }) => {
    const errors = trackPageErrors(page);
    await page.goto('/rules/new');
    await expect(page.locator('app-rule-builder').first()).toBeVisible({ timeout: 15000 });

    const notifyBtn = page.getByRole('button', { name: /\u041e\u043f\u043e\u0432\u0435\u0449\u0435\u043d\u0438\u0435/i });
    const pushBtn = page.getByRole('button', { name: /Push \(in-app\)/i });
    await expect(notifyBtn).toBeVisible();
    await expect(pushBtn).toBeVisible();

    await notifyBtn.click();
    await pushBtn.click();
    await expect(page.locator('.block[data-type="notify"]').first()).toBeVisible();
    await expect(page.locator('.block[data-type="push"]').first()).toBeVisible();

    await page.waitForLoadState('networkidle');
    expectNoFatalErrors(errors);
  });
});

import { test, expect, loginAsAdmin } from './fixtures/auth';
import { resolveIngestApiKey } from './fixtures/ingest-source';
import { loginAdminApi } from './fixtures/cleanup';
import type { APIRequestContext, Page, Response } from '@playwright/test';

function uniqueSuffix(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

async function dismissToasts(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelectorAll('app-push-toast').forEach((el) => el.remove());
  });
}

function isEventsListResponse(response: Response): boolean {
  if (response.request().method() !== 'GET' || !response.ok()) return false;
  let pathname: string;
  try {
    pathname = new URL(response.url()).pathname.replace(/\/$/, '');
  } catch {
    return false;
  }
  return pathname === '/api/v1/events';
}

function eventsListProductId(response: Response): string | null {
  try {
    return new URL(response.url()).searchParams.get('productId');
  } catch {
    return null;
  }
}

async function gotoHealth(page: Page): Promise<void> {
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

async function openProductPage(page: Page, productName: string): Promise<void> {
  await heatCellForProduct(page, productName).locator('.heat-cell').click();
  await dismissToasts(page);
  await page.locator('.footer-link a').click();
  await expect(page).toHaveURL(/\/health\/[0-9a-f-]+$/);
  await expect(page.locator('app-health-product h1')).toHaveText(productName, { timeout: 15000 });
}

async function createCi(
  request: APIRequestContext,
  token: string,
  fqdn: string,
): Promise<string> {
  const created = await request.post('/api/v1/admin/configuration-items', {
    headers: { Authorization: `Bearer ${token}` },
    data: { fqdn, ciType: 'host', system: 'e2e', subsystem: 'web' },
  });
  expect(created.ok(), `create CI failed: ${created.status()} ${await created.text()}`).toBeTruthy();
  const body = (await created.json()) as { id: string };
  expect(body.id).toBeTruthy();
  return body.id;
}

async function createProduct(
  request: APIRequestContext,
  token: string,
  opts: { name: string; code: string; ciId: string },
): Promise<string> {
  const created = await request.post('/api/v1/admin/products', {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: opts.name,
      code: opts.code,
      tenant: 'e2e-tenant',
      site: 'e2e-site',
      ciIds: [opts.ciId],
    },
  });
  expect(created.ok(), `create product failed: ${created.status()} ${await created.text()}`).toBeTruthy();
  const body = (await created.json()) as { id: string };
  expect(body.id).toBeTruthy();
  return body.id;
}

async function ingestEvent(
  request: APIRequestContext,
  opts: { title: string; fqdn: string; severity: string; suffix: string },
): Promise<void> {
  const apiKey = await resolveIngestApiKey(request);
  const ingest = await request.post('/api/v1/ingest', {
    headers: { 'X-Api-Key': apiKey },
    data: {
      events: [
        {
          externalId: `e2e-hspf-${opts.severity}-${opts.suffix}-${Math.random().toString(36).slice(2, 8)}`,
          title: opts.title,
          description: 'E2E health-signals product filter',
          severity: opts.severity,
          occurredAt: new Date().toISOString(),
          nodeFqdn: opts.fqdn,
        },
      ],
    },
  });
  expect(ingest.ok(), `ingest failed: ${ingest.status()} ${await ingest.text()}`).toBeTruthy();
}

async function waitForEventTitle(
  request: APIRequestContext,
  token: string,
  title: string,
  params: Record<string, string> = {},
): Promise<void> {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const list = await request.get('/api/v1/events', {
      headers: { Authorization: `Bearer ${token}` },
      params: { size: 200, includeSilenced: true, sort: 'createdAt,desc', ...params },
    });
    expect(list.ok()).toBeTruthy();
    const body = (await list.json()) as { items: Array<{ title: string }> };
    if (body.items.some((item) => item.title === title)) return;
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
  throw new Error(`Ingested event not listed: ${title}`);
}

async function setupTwoProducts(request: APIRequestContext) {
  const suffix = uniqueSuffix();
  const token = await loginAdminApi(request);
  const fqdnA = `e2e-hspf-a-${suffix}.wisla.local`;
  const fqdnB = `e2e-hspf-b-${suffix}.wisla.local`;
  const ciA = await createCi(request, token, fqdnA);
  const ciB = await createCi(request, token, fqdnB);
  const nameA = `E2E HSPF A ${suffix}`;
  const nameB = `E2E HSPF B ${suffix}`;
  const productAId = await createProduct(request, token, {
    name: nameA,
    code: `e2e-hspf-a-${suffix}`,
    ciId: ciA,
  });
  const productBId = await createProduct(request, token, {
    name: nameB,
    code: `e2e-hspf-b-${suffix}`,
    ciId: ciB,
  });

  const titleAMajor = `E2E HSPF A major ${suffix}`;
  const titleACritical = `E2E HSPF A critical ${suffix}`;
  const titleBMajor = `E2E HSPF B major ${suffix}`;
  await ingestEvent(request, { title: titleAMajor, fqdn: fqdnA, severity: 'major', suffix });
  await ingestEvent(request, { title: titleACritical, fqdn: fqdnA, severity: 'critical', suffix });
  await ingestEvent(request, { title: titleBMajor, fqdn: fqdnB, severity: 'major', suffix });
  await waitForEventTitle(request, token, titleAMajor, { productId: productAId });
  await waitForEventTitle(request, token, titleBMajor, { productId: productBId });

  return {
    productAId,
    productBId,
    ciA,
    nameA,
    titleAMajor,
    titleACritical,
    titleBMajor,
  };
}

function productIdChip(page: Page, productId: string) {
  return page.locator('app-query-bar .chip').filter({ hasText: new RegExp(`productId\\s*=\\s*${productId}`) });
}

function ciIdChip(page: Page, ciId: string) {
  return page.locator('app-query-bar .chip').filter({ hasText: new RegExp(`ciId\\s*=\\s*${ciId}`) });
}

test.describe('Health signals product filter', () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await dismissToasts(page);
  });

  test('Перейти к сигналам / В консоль open console with productId chip', async ({ page, request }) => {
    const ctx = await setupTwoProducts(request);
    await gotoHealth(page);
    await expect(heatCellForProduct(page, ctx.nameA)).toBeVisible({ timeout: 15000 });
    await openProductPage(page, ctx.nameA);

    const headerLink = page.getByRole('link', { name: 'В консоль' });
    await expect(headerLink).toBeVisible();
    await expect(headerLink).toHaveAttribute('href', new RegExp(`/console\\?productId=${ctx.productAId}`));

    const signalsLink = page.getByRole('link', { name: /Перейти к сигналам/ });
    await expect(signalsLink).toBeVisible();
    await expect(signalsLink).toHaveAttribute('href', new RegExp(`/console\\?productId=${ctx.productAId}`));
    await expect(signalsLink).not.toHaveAttribute('href', /[?&]ci=/);

    await headerLink.click();
    await expect(page).toHaveURL(new RegExp(`/console\\?productId=${ctx.productAId}`));
    await expect(productIdChip(page, ctx.productAId)).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table tbody')).toContainText(ctx.titleAMajor);
    await expect(page.locator('table.data-table tbody')).not.toContainText(ctx.titleBMajor);
  });

  test('Direct URL applies productId without a health click', async ({ page, request }) => {
    const ctx = await setupTwoProducts(request);
    await page.goto(`/console?productId=${ctx.productAId}`);
    await expect(productIdChip(page, ctx.productAId)).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table tbody')).toContainText(ctx.titleAMajor);
    await expect(page.locator('table.data-table tbody')).not.toContainText(ctx.titleBMajor);
  });

  test('Clearing the productId chip removes the filter; invalid UUID adds no chip', async ({
    page,
    request,
  }) => {
    const ctx = await setupTwoProducts(request);

    await page.goto('/console?productId=not-a-uuid');
    await expect(page.locator('app-query-bar').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('app-query-bar .chip').filter({ hasText: 'productId' })).toHaveCount(0);

    await page.goto(`/console?productId=${ctx.productAId}`);
    await expect(productIdChip(page, ctx.productAId)).toBeVisible({ timeout: 15000 });

    const unfiltered = page.waitForResponse(
      (response) => isEventsListResponse(response) && eventsListProductId(response) === null,
    );
    await page.locator('app-query-bar .chip-remove').click();
    await unfiltered;
    await expect(page.locator('app-query-bar .chip').filter({ hasText: 'productId' })).toHaveCount(0);
    await expect(page.locator('table.data-table tbody')).toContainText(ctx.titleBMajor);
  });

  test('productId AND severity; health graph snapshot stays unchanged', async ({ page, request }) => {
    const ctx = await setupTwoProducts(request);
    await page.goto(`/console?productId=${ctx.productAId}&severity=major`);
    await expect(productIdChip(page, ctx.productAId)).toBeVisible({ timeout: 15000 });
    await expect(page.locator('app-query-bar .chip').filter({ hasText: /severity\s*=\s*major/ })).toBeVisible();
    await expect(page.locator('table.data-table tbody')).toContainText(ctx.titleAMajor);
    await expect(page.locator('table.data-table tbody')).not.toContainText(ctx.titleACritical);
    await expect(page.locator('table.data-table tbody')).not.toContainText(ctx.titleBMajor);

    await gotoHealth(page);
    await openProductPage(page, ctx.nameA);
    await expect(page.locator('.count').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.count').first()).toContainText('%');
    await expect(page.locator('app-monq-health-graph')).toBeVisible();
  });

  test('Сигналы tab «Открыть в консоли» opens console filtered by that CI', async ({ page, request }) => {
    const ctx = await setupTwoProducts(request);
    await gotoHealth(page);
    await expect(heatCellForProduct(page, ctx.nameA)).toBeVisible({ timeout: 15000 });
    await openProductPage(page, ctx.nameA);

    await page.locator('.operative').scrollIntoViewIfNeeded();
    await dismissToasts(page);
    const signalsTab = page.locator('app-operative-center-panel .tabs button.tab', { hasText: 'Сигналы' });
    await signalsTab.evaluate((el: HTMLElement) => el.click());
    const signalsConsoleLink = page.locator('app-operative-center-panel a.link').filter({ hasText: /Открыть в консоли/ });
    await expect(signalsConsoleLink).toBeVisible({ timeout: 15000 });
    await expect(signalsConsoleLink).toHaveAttribute('href', new RegExp(`/console\\?ciId=${ctx.ciA}`));
    await expect(signalsConsoleLink).not.toHaveAttribute('href', /[?&]ci=/);
    await expect(signalsConsoleLink).not.toHaveAttribute('href', /[?&]productId=/);

    const consoleHref = await signalsConsoleLink.getAttribute('href');
    expect(consoleHref).toBeTruthy();
    await page.goto(consoleHref!);
    await expect(page).toHaveURL(new RegExp(`/console\\?ciId=${ctx.ciA}`));
    await expect(ciIdChip(page, ctx.ciA)).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table tbody')).toContainText(ctx.titleAMajor);
    await expect(page.locator('table.data-table tbody')).not.toContainText(ctx.titleBMajor);
  });
});

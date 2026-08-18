import { test, expect, loginAsAdmin, E2E_ADMIN_LOGIN, E2E_ADMIN_PASSWORD } from './fixtures/auth';
import { resolveIngestApiKey } from './fixtures/ingest-source';
import type { APIRequestContext, Page, Response } from '@playwright/test';

const DASHBOARD_SEVERITIES = [
  { key: 'critical', label: 'Critical', badge: 'Критично' },
  { key: 'major', label: 'Major', badge: 'Существенно' },
  { key: 'minor', label: 'Minor', badge: 'Незначительно' },
  { key: 'warning', label: 'Warning', badge: 'Предупреждение' },
] as const;

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

function eventsListSeverity(response: Response): string | null {
  try {
    return new URL(response.url()).searchParams.get('severity');
  } catch {
    return null;
  }
}

async function loginAdminApi(request: APIRequestContext): Promise<string> {
  const response = await request.post('/api/v1/auth/login', {
    data: { login: E2E_ADMIN_LOGIN, password: E2E_ADMIN_PASSWORD },
  });
  expect(response.ok(), `login failed: ${response.status()}`).toBeTruthy();
  const body = (await response.json()) as { accessToken?: string };
  expect(body.accessToken).toBeTruthy();
  return body.accessToken!;
}

async function ingestMixedSeverityEvents(request: APIRequestContext): Promise<Record<string, string>> {
  const suffix = uniqueSuffix();
  const titles = {
    critical: `E2E Sev Critical ${suffix}`,
    major: `E2E Sev Major ${suffix}`,
    minor: `E2E Sev Minor ${suffix}`,
    warning: `E2E Sev Warning ${suffix}`,
  };

  const apiKey = await resolveIngestApiKey(request);
  const ingest = await request.post('/api/v1/ingest', {
    headers: { 'X-Api-Key': apiKey },
    data: {
      events: DASHBOARD_SEVERITIES.map(({ key }) => ({
        externalId: `e2e-sev-${key}-${suffix}`,
        title: titles[key],
        description: `E2E dashboard priority filter ${key}`,
        severity: key,
        occurredAt: new Date().toISOString(),
        nodeFqdn: 'demo-server.wisla.local',
      })),
    },
  });
  expect(ingest.ok(), `ingest failed: ${ingest.status()} ${await ingest.text()}`).toBeTruthy();

  const token = await loginAdminApi(request);
  const expected = Object.values(titles);
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const list = await request.get('/api/v1/events', {
      headers: { Authorization: `Bearer ${token}` },
      params: { size: 200, includeSilenced: true, sort: 'createdAt,desc' },
    });
    expect(list.ok()).toBeTruthy();
    const body = (await list.json()) as { items: Array<{ title: string }> };
    const found = new Set(body.items.map((item) => item.title));
    if (expected.every((title) => found.has(title))) return titles;
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
  throw new Error(`Ingested mixed-severity events not listed: ${expected.join(', ')}`);
}

async function expectSeverityChip(page: Page, severity: string): Promise<void> {
  await expect(page.locator('app-query-bar .chip')).toBeVisible({ timeout: 15000 });
  await expect(page.locator('app-query-bar .chip')).toContainText(new RegExp(`severity\\s*=\\s*${severity}`));
}

async function expectTableOnlySeverity(page: Page, badge: string): Promise<void> {
  const rows = page.locator('table.data-table tbody tr');
  await expect(rows.first()).toBeVisible({ timeout: 15000 });
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    await expect(rows.nth(i).locator('app-severity-badge')).toContainText(badge);
  }
}

async function expectFilteredConsole(page: Page, key: string, badge: string): Promise<void> {
  await expect(page).toHaveURL(new RegExp(`/console\\?severity=${key}`));
  await expect(page.locator('app-query-bar').first()).toBeVisible({ timeout: 15000 });
  await expectSeverityChip(page, key);
  await expectTableOnlySeverity(page, badge);
}

test.describe('Dashboard priority filter', () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await dismissToasts(page);
  });

  test('Dashboard Critical/Major/Minor/Warning open filtered console', async ({ page, request }) => {
    await ingestMixedSeverityEvents(request);

    for (const { key, label, badge } of DASHBOARD_SEVERITIES) {
      await page.goto('/');
      await expect(page.locator('a.severity-card').filter({ hasText: label })).toBeVisible({ timeout: 15000 });
      await page.locator('a.severity-card').filter({ hasText: label }).click();
      await expectFilteredConsole(page, key, badge);
    }
  });

  test('Direct URL applies severity without dashboard click', async ({ page, request }) => {
    await ingestMixedSeverityEvents(request);
    await page.goto('/console?severity=major');
    await expectFilteredConsole(page, 'major', 'Существенно');
  });

  test('Clearing the severity chip restores the unfiltered list', async ({ page, request }) => {
    await ingestMixedSeverityEvents(request);
    await page.goto('/console?severity=major');
    await expectFilteredConsole(page, 'major', 'Существенно');

    const unfiltered = page.waitForResponse(
      (response) => isEventsListResponse(response) && eventsListSeverity(response) === null,
    );
    await page.locator('app-query-bar .chip-remove').click();
    await unfiltered;

    await expect(page.locator('app-query-bar .chip')).toHaveCount(0);
    const badges = page.locator('table.data-table tbody tr app-severity-badge');
    await expect(badges.first()).toBeVisible({ timeout: 15000 });
    const labels = await badges.allTextContents();
    const unique = new Set(labels.map((text) => text.trim()).filter(Boolean));
    expect(unique.size).toBeGreaterThan(1);
  });

  test('Dashboard severity counts stay unchanged', async ({ page, request }) => {
    await ingestMixedSeverityEvents(request);
    await page.goto('/');
    await expect(page.locator('a.severity-card').filter({ hasText: 'Critical' })).toBeVisible({ timeout: 15000 });

    await page.locator('a.severity-card').filter({ hasText: 'Critical' }).click();
    await expectFilteredConsole(page, 'critical', 'Критично');

    await page.goto('/');
    for (const { label } of DASHBOARD_SEVERITIES) {
      await expect(page.locator('a.severity-card').filter({ hasText: label })).toBeVisible();
    }
    await expect(page.locator('a.severity-card')).toHaveCount(4);
  });
});

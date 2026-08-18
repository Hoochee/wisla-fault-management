import { test, expect, loginAsAdmin, E2E_ADMIN_LOGIN, E2E_ADMIN_PASSWORD } from './fixtures/auth';
import type { APIRequestContext, Page, Request, Response } from '@playwright/test';

const DEMO_SOURCE_API_KEY = 'demo-source-key';

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

function isEventActionRequest(request: Request, eventId: string): boolean {
  if (request.method() !== 'POST') return false;
  let pathname: string;
  try {
    pathname = new URL(request.url()).pathname.replace(/\/$/, '');
  } catch {
    return false;
  }
  return pathname === `/api/v1/events/${eventId}/actions`;
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

async function ingestDutyEvent(request: APIRequestContext, title: string): Promise<string> {
  const ingest = await request.post('/api/v1/ingest', {
    headers: { 'X-Api-Key': DEMO_SOURCE_API_KEY },
    data: {
      events: [
        {
          externalId: `e2e-duty-${uniqueSuffix()}`,
          title,
          description: 'E2E duty action event',
          severity: 'warning',
          occurredAt: new Date().toISOString(),
          nodeFqdn: 'demo-server.wisla.local',
        },
      ],
    },
  });
  expect(ingest.ok(), `ingest failed: ${ingest.status()} ${await ingest.text()}`).toBeTruthy();

  const token = await loginAdminApi(request);
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const list = await request.get('/api/v1/events', {
      headers: { Authorization: `Bearer ${token}` },
      params: { size: 200, includeSilenced: true, sort: 'createdAt,desc' },
    });
    expect(list.ok()).toBeTruthy();
    const body = (await list.json()) as { items: Array<{ id: string; title: string }> };
    const found = body.items.find((item) => item.title === title);
    if (found) return found.id;
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
  throw new Error(`Ingested event not listed: ${title}`);
}

function dutyBar(page: Page) {
  return page.locator('app-event-duty-actions').first();
}

async function expectDutyButtons(root: ReturnType<Page['locator']>): Promise<void> {
  await expect(root.getByRole('button', { name: 'Принять в работу' })).toBeVisible();
  await expect(root.getByRole('button', { name: 'Закрыть' })).toBeVisible();
  await expect(root.getByRole('button', { name: 'Подтвердить' })).toBeVisible();
  await expect(root.getByRole('button', { name: 'Комментарий' })).toBeVisible();
  await expect(root.getByText('Назначить', { exact: false })).toBeVisible();
  await expect(root.getByRole('button', { name: '15', exact: true })).toBeVisible();
  await expect(root.getByRole('button', { name: '30', exact: true })).toBeVisible();
  await expect(root.getByRole('button', { name: '60', exact: true })).toBeVisible();
}

async function submitComment(page: Page, text: string): Promise<void> {
  await page.getByRole('button', { name: 'Комментарий' }).click();
  const modal = page.locator('.modal');
  await expect(modal.getByRole('heading', { name: 'Комментарий' })).toBeVisible();
  await modal.locator('textarea').fill(text);
  await modal.getByRole('button', { name: 'Отправить' }).click();
  await expect(modal).toHaveCount(0, { timeout: 15000 });
}

async function assignFirstActiveUser(bar: ReturnType<Page['locator']>): Promise<string> {
  const select = bar.locator('select.assign-select');
  await expect(select.locator('option').nth(1)).toBeAttached({ timeout: 15000 });
  const option = select.locator('option').nth(1);
  const userId = (await option.getAttribute('value')) ?? '';
  const fullName = ((await option.textContent()) ?? '').trim();
  expect(userId).toBeTruthy();
  expect(fullName).toBeTruthy();
  await select.selectOption(userId);
  return fullName;
}

async function clickSilenceAndAssertBody(
  page: Page,
  eventId: string,
  minutes: 15 | 30 | 60,
): Promise<void> {
  const pending = page.waitForRequest((req) => isEventActionRequest(req, eventId));
  await dutyBar(page).getByRole('button', { name: String(minutes), exact: true }).click();
  const request = await pending;
  const body = request.postDataJSON() as { action?: string; silenceMinutes?: number };
  expect(body.action).toBe('silence');
  expect(body.silenceMinutes).toBe(minutes);
  await expect(page.locator('.silence-indicator')).toBeVisible({ timeout: 15000 });
}

test.describe('Event duty actions', () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await dismissToasts(page);
  });

  test('card /console/:eventId duty buttons, ack, comment, assign, silence presets', async ({
    page,
    request,
  }) => {
    const suffix = uniqueSuffix();
    const title = `E2E Duty Card ${suffix}`;
    const commentText = `Проверил карточку ${suffix}`;
    const eventId = await ingestDutyEvent(request, title);

    await page.goto(`/console/${eventId}`);
    await expect(page.locator('app-event-card h1')).toHaveText(title, { timeout: 15000 });
    await expectDutyButtons(dutyBar(page));
    await expect(page.locator('.bulk-toolbar')).toHaveCount(0);
    await expect(page.locator('.context-menu')).toHaveCount(0);

    await dutyBar(page).getByRole('button', { name: 'Подтвердить' }).click();
    await expect(page.locator('.ack-indicator')).toContainText('Подтверждено', { timeout: 15000 });

    await page.reload();
    await expect(page.locator('app-event-card h1')).toHaveText(title, { timeout: 15000 });
    await expect(page.locator('.ack-indicator')).toContainText('Подтверждено');

    await submitComment(page, commentText);
    await expect(page.locator('.log-action').filter({ hasText: 'Комментарий' })).toBeVisible({
      timeout: 15000,
    });
    await expect(page.locator('.log-details').filter({ hasText: commentText })).toBeVisible();

    const assignee = await assignFirstActiveUser(dutyBar(page));
    await expect(page.locator('.info-block').filter({ hasText: 'Исполнитель' })).toContainText(
      assignee,
      { timeout: 15000 },
    );
    await expect(page.locator('app-status-badge .badge')).toHaveText('Новое');
    await expect(page.locator('app-status-badge .badge')).not.toHaveText('В работе');

    await clickSilenceAndAssertBody(page, eventId, 15);
    await clickSilenceAndAssertBody(page, eventId, 30);
    await clickSilenceAndAssertBody(page, eventId, 60);
  });

  test('console bottom panel acts on selected row; silenced event omitted from list', async ({
    page,
    request,
  }) => {
    const suffix = uniqueSuffix();
    const title = `E2E Duty Console ${suffix}`;
    const commentText = `Комментарий с панели ${suffix}`;
    const eventId = await ingestDutyEvent(request, title);

    await page.goto('/console');
    await expect(page.locator('app-query-bar').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('table.data-table')).toBeVisible({ timeout: 15000 });

    await page.locator('app-query-bar input.search').fill(title);
    const row = page.locator('table.data-table tbody tr').filter({ hasText: title });
    await expect(row).toBeVisible({ timeout: 15000 });
    await row.click();

    const panel = page.locator('.detail-panel');
    await expect(panel).toBeVisible();
    await expect(panel.locator('strong')).toHaveText(title);
    await expectDutyButtons(panel.locator('app-event-duty-actions'));
    await expect(page.locator('.bulk-toolbar')).toHaveCount(0);
    await expect(page.locator('.context-menu')).toHaveCount(0);

    await panel.getByRole('button', { name: 'Подтвердить' }).click();
    await expect(panel.locator('.ack-indicator')).toContainText('Подтверждено', { timeout: 15000 });

    await submitComment(page, commentText);
    await assignFirstActiveUser(panel.locator('app-event-duty-actions'));
    await expect(row.locator('app-status-badge .badge')).toHaveText('Новое');
    await expect(row.locator('app-status-badge .badge')).not.toHaveText('В работе');

    const omittedFromDefaultList = page.waitForResponse(async (response) => {
      if (!isEventsListResponse(response)) return false;
      const includeSilenced = new URL(response.url()).searchParams.get('includeSilenced');
      if (includeSilenced === 'true') return false;
      const listBody = (await response.json()) as { items: Array<{ id: string; title: string }> };
      return !listBody.items.some((item) => item.id === eventId || item.title === title);
    });
    const silencePost = page.waitForRequest((req) => isEventActionRequest(req, eventId));
    await panel.getByRole('button', { name: '15', exact: true }).click();
    const posted = await silencePost;
    expect(posted.postDataJSON()).toMatchObject({ action: 'silence', silenceMinutes: 15 });

    const listResponse = await omittedFromDefaultList;
    expect(new URL(listResponse.url()).searchParams.get('includeSilenced')).not.toBe('true');
    await expect(row).toHaveCount(0, { timeout: 15000 });
  });
});

import { expect, type APIRequestContext } from '@playwright/test';
import { loginAdminApi } from './cleanup';

export const DEMO_SOURCE_API_KEY = 'demo-source-key';

let cachedKey: string | undefined;

export async function resolveIngestApiKey(request: APIRequestContext): Promise<string> {
  if (cachedKey) {
    return cachedKey;
  }

  const probe = await request.post('/api/v1/ingest', {
    headers: { 'X-Api-Key': DEMO_SOURCE_API_KEY },
    data: { events: [] },
  });
  if (probe.status() !== 401) {
    cachedKey = DEMO_SOURCE_API_KEY;
    return cachedKey;
  }

  const token = await loginAdminApi(request);
  const suffix = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
  const created = await request.post('/api/v1/sources', {
    headers: { Authorization: 'Bearer ' + token },
    data: {
      name: 'E2E Ingest ' + suffix,
      type: 'push_rest',
      protocol: 'HTTP',
      endpoint: 'http://localhost:8081/webhook/e2e-' + suffix,
      status: 'active',
    },
  });
  expect(created.ok(), 'create source failed: ' + created.status() + ' ' + (await created.text())).toBeTruthy();
  const body = (await created.json()) as { apiKey?: string };
  expect(body.apiKey, 'create source did not return apiKey').toBeTruthy();
  cachedKey = body.apiKey!;
  return cachedKey;
}

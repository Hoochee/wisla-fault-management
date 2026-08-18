import type { APIRequestContext } from '@playwright/test';
import { E2E_ADMIN_LOGIN, E2E_ADMIN_PASSWORD } from './credentials';

const E2E_CODE_PREFIX = 'e2e-';
const E2E_NAME_PREFIX = 'E2E ';
const PROTECTED_CODES = new Set(['prod-billing']);
const PROTECTED_NAMES = new Set(['Billing Platform']);

export interface AdminProductSummary {
  id: string;
  code: string;
  name: string;
}

export function isE2eLeftoverProduct(product: Pick<AdminProductSummary, 'code' | 'name'>): boolean {
  if (PROTECTED_CODES.has(product.code) || PROTECTED_NAMES.has(product.name)) {
    return false;
  }
  return product.code.startsWith(E2E_CODE_PREFIX) || product.name.startsWith(E2E_NAME_PREFIX);
}

export async function loginAdminApi(request: APIRequestContext): Promise<string> {
  let response;
  try {
    response = await request.post('/api/v1/auth/login', {
      data: { login: E2E_ADMIN_LOGIN, password: E2E_ADMIN_PASSWORD },
    });
  } catch (err) {
    throw new Error(`E2E product teardown cannot reach backend login: ${String(err)}`);
  }
  if (!response.ok()) {
    throw new Error(`E2E product teardown login failed: ${response.status()} ${await response.text()}`);
  }
  const body = (await response.json()) as { accessToken?: string };
  if (!body.accessToken) {
    throw new Error('E2E product teardown login failed: missing accessToken');
  }
  return body.accessToken;
}

export async function unlinkAndDeleteProduct(
  request: APIRequestContext,
  token: string,
  productId: string,
): Promise<void> {
  const headers = { Authorization: `Bearer ${token}` };
  const unlink = await request.patch(`/api/v1/admin/products/${productId}`, {
    headers,
    data: { ciIds: [] },
  });
  if (!unlink.ok()) {
    throw new Error(`unlink failed ${unlink.status()}: ${await unlink.text()}`);
  }
  const deleted = await request.delete(`/api/v1/admin/products/${productId}`, { headers });
  if (!deleted.ok()) {
    throw new Error(`delete failed ${deleted.status()}: ${await deleted.text()}`);
  }
}

export async function deleteLeftoverE2eProducts(request: APIRequestContext): Promise<void> {
  const token = await loginAdminApi(request);
  let list;
  try {
    list = await request.get('/api/v1/admin/products', {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (err) {
    throw new Error(`E2E product teardown cannot list admin products: ${String(err)}`);
  }
  if (!list.ok()) {
    throw new Error(`E2E product teardown list failed: ${list.status()} ${await list.text()}`);
  }
  const body: unknown = await list.json();
  if (!Array.isArray(body)) {
    throw new Error('E2E product teardown expected a product array from GET /api/v1/admin/products');
  }

  const leftovers = (body as AdminProductSummary[]).filter(isE2eLeftoverProduct);
  const errors: string[] = [];
  for (const product of leftovers) {
    try {
      await unlinkAndDeleteProduct(request, token, product.id);
      console.log(`[e2e cleanup] deleted ${product.code} "${product.name}" (${product.id})`);
    } catch (err) {
      const message = `[e2e cleanup] failed ${product.code} "${product.name}" (${product.id}): ${String(err)}`;
      console.error(message);
      errors.push(message);
    }
  }

  if (errors.length > 0) {
    throw new Error(`${errors.length} e2e product(s) could not be deleted:\n${errors.join('\n')}`);
  }
}

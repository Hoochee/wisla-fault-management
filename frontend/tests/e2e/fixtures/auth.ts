import { test as base, expect, type Page } from '@playwright/test';
import { E2E_ADMIN_LOGIN, E2E_ADMIN_PASSWORD } from './credentials';

export { E2E_ADMIN_LOGIN, E2E_ADMIN_PASSWORD };

export async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('input[formcontrolname="login"]').fill(E2E_ADMIN_LOGIN);
  await page.locator('input[formcontrolname="password"]').fill(E2E_ADMIN_PASSWORD);
  await page.getByRole('button', { name: /войти/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

export const test = base.extend({
  authenticatedPage: async ({ page }, use) => {
    await loginAsAdmin(page);
    await use(page);
  },
});

export { expect };
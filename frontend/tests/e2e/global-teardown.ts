import { request, type FullConfig } from '@playwright/test';
import { deleteLeftoverE2eProducts } from './fixtures/cleanup';

function resolveBaseURL(config: FullConfig): string {
  const fromProject = config.projects[0]?.use?.baseURL;
  if (typeof fromProject === 'string' && fromProject.length > 0) {
    return fromProject;
  }
  return process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080';
}

export default async function globalTeardown(config: FullConfig): Promise<void> {
  const baseURL = resolveBaseURL(config);
  const api = await request.newContext({ baseURL });
  try {
    await deleteLeftoverE2eProducts(api);
  } finally {
    await api.dispose();
  }
}

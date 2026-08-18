import { describe, expect, it } from 'vitest';
import { isE2eLeftoverProduct } from '../e2e/fixtures/cleanup';

describe('isE2eLeftoverProduct', () => {
  it('matches health e2e codes and names', () => {
    expect(isE2eLeftoverProduct({ code: 'e2e-prod-abc', name: 'E2E Product abc' })).toBe(true);
    expect(isE2eLeftoverProduct({ code: 'e2e-del-abc', name: 'E2E Delete abc' })).toBe(true);
    expect(isE2eLeftoverProduct({ code: 'e2e-hp-abc', name: 'E2E HealthPct abc' })).toBe(true);
    expect(isE2eLeftoverProduct({ code: 'e2e-wt-abc', name: 'E2E Weight abc' })).toBe(true);
  });

  it('never matches the seeded billing product', () => {
    expect(isE2eLeftoverProduct({ code: 'prod-billing', name: 'Billing Platform' })).toBe(false);
    expect(isE2eLeftoverProduct({ code: 'prod-billing', name: 'E2E renamed' })).toBe(false);
    expect(isE2eLeftoverProduct({ code: 'e2e-billing', name: 'Billing Platform' })).toBe(false);
  });

  it('does not match unrelated products', () => {
    expect(isE2eLeftoverProduct({ code: 'core-api', name: 'Core API' })).toBe(false);
    expect(isE2eLeftoverProduct({ code: 'e2eprod', name: 'E2EProduct' })).toBe(false);
  });
});

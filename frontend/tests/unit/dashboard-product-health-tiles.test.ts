import { ComponentFixture, getTestBed, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import {
  ConfigurationItem,
  DashboardSummary,
  Event,
  EventMap,
  ProductHealth,
  ProductHealthDetail,
} from '../../src/app/core/api/api.models';
import { FmApiService } from '../../src/app/core/api/fm-api.service';
import { buildProfiles, HEALTH_LABELS, percentToLevel } from '../../src/app/core/health/health-profile.util';
import { DashboardPageComponent } from '../../src/app/pages/dashboard/dashboard-page.component';

const GIFT_SHOP_ID = 'gift-shop-1';
const LOW_HEALTH_ID = 'low-health-1';
const MISSING_SNAPSHOT_ID = 'no-snapshot-1';

const GIFT_SHOP_CIS: ConfigurationItem[] = [
  ci('ci-storefront', 'giftshop-storefront.demo', 'service'),
  ci('ci-catalog', 'giftshop-catalog.demo', 'service'),
  ci('ci-checkout', 'giftshop-checkout.demo', 'service'),
  ci('ci-postgres', 'giftshop-postgres.demo', 'database'),
];

const GIFT_SHOP_EVENTS: Event[] = [
  event({ id: 'ev-storefront', ciId: 'ci-storefront', severity: 'major', nodeFqdn: 'giftshop-storefront.demo' }),
  event({ id: 'ev-checkout', ciId: 'ci-checkout', severity: 'critical', nodeFqdn: 'giftshop-checkout.demo' }),
];

const SYSTEM_MAPS: EventMap[] = [
  { id: 'map-active', name: 'Активные', query: '', isSystem: true, isPersonal: false, columns: [] },
  { id: 'map-closed', name: 'Закрытые', query: '', isSystem: true, isPersonal: false, columns: [] },
];

const SLOT_CODES = ['POWER', 'CPU', 'HDD', 'AVAILABILITY'] as const;

function ci(id: string, fqdn: string, ciType: string): ConfigurationItem {
  return {
    id,
    fqdn,
    ciType,
    system: 'Gift Shop',
    subsystem: '',
    software: '',
    products: [GIFT_SHOP_ID],
    tags: ['giftshop'],
  };
}

function event(overrides: Partial<Event> & Pick<Event, 'id' | 'ciId'>): Event {
  return {
    status: 'new',
    severity: 'warning',
    title: 'alert',
    description: '',
    repeatCount: 1,
    nodeFqdn: '',
    systemName: 'Gift Shop',
    sourceId: 'src-1',
    createdAt: '2026-08-19T00:00:00Z',
    sourceAt: '2026-08-19T00:00:00Z',
    ...overrides,
  };
}

function slot(
  code: string,
  healthPercent: number,
  ciIds: string[] = [],
): NonNullable<ProductHealth['components']>[number] {
  return {
    code,
    name: code,
    healthPercent,
    damagePercent: 100 - healthPercent,
    ciIds,
  };
}

function product(overrides: Partial<ProductHealth> & Pick<ProductHealth, 'id' | 'name'>): ProductHealth {
  return {
    tenant: 'demo',
    site: 'lab',
    maxSeverity: 'normal',
    activeEventCount: 0,
    ciIds: [],
    tags: [],
    healthPercent: 100,
    damagePercent: 0,
    components: [],
    ...overrides,
  };
}

function giftShop(): ProductHealth {
  return product({
    id: GIFT_SHOP_ID,
    name: 'Gift Shop',
    healthPercent: 75,
    damagePercent: 25,
    maxSeverity: 'major',
    activeEventCount: 42,
    ciIds: GIFT_SHOP_CIS.map((item) => item.id),
    components: [
      slot('POWER', 50, ['ci-storefront']),
      slot('CPU', 80, ['ci-catalog']),
      slot('HDD', 90, ['ci-postgres']),
      slot('AVAILABILITY', 100, ['ci-checkout']),
    ],
  });
}

function giftShopDetail(): ProductHealthDetail {
  return {
    ...giftShop(),
    configurationItems: GIFT_SHOP_CIS,
    activeEvents: GIFT_SHOP_EVENTS,
    minHealthToday: 75,
    maxHealthToday: 75,
  };
}

function emptyDetail(row: ProductHealth): ProductHealthDetail {
  return {
    ...row,
    configurationItems: [],
    activeEvents: [],
  };
}

function previewOnly(index: number): ProductHealth {
  return product({
    id: `preview-${index}`,
    name: `Preview Only ${index}`,
    healthPercent: 10,
    maxSeverity: 'critical',
    activeEventCount: 99,
  });
}

function summary(overrides: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    severityCounts: {
      fatal: 0,
      critical: 3,
      major: 2,
      minor: 1,
      warning: 4,
      normal: 0,
    },
    totalActive: 10,
    productPreview: [1, 2, 3, 4, 5].map(previewOnly),
    systemMaps: SYSTEM_MAPS,
    ...overrides,
  };
}

function sixHealthRows(): ProductHealth[] {
  return [
    giftShop(),
    product({
      id: LOW_HEALTH_ID,
      name: 'Low Health',
      healthPercent: 25,
      maxSeverity: 'warning',
      activeEventCount: 1,
    }),
    product({ id: 'p-3', name: 'Catalog' }),
    product({ id: 'p-4', name: 'Checkout' }),
    product({ id: 'p-5', name: 'Payments' }),
    product({ id: 'p-6', name: 'Storefront' }),
  ];
}

async function setup(opts: {
  summary?: DashboardSummary;
  products?: ProductHealth[];
  details?: Record<string, ProductHealthDetail>;
  productsError?: boolean;
  productDetailErrorIds?: string[];
  summaryError?: boolean;
} = {}): Promise<{
  fixture: ComponentFixture<DashboardPageComponent>;
  el: HTMLElement;
  api: {
    getDashboardSummary: ReturnType<typeof vi.fn>;
    getProducts: ReturnType<typeof vi.fn>;
    getProduct: ReturnType<typeof vi.fn>;
  };
}> {
  const products = opts.products ?? sixHealthRows();
  const details = opts.details ?? {
    [GIFT_SHOP_ID]: giftShopDetail(),
  };
  const failDetail = new Set(opts.productDetailErrorIds ?? []);

  const api = {
    getDashboardSummary: vi.fn().mockReturnValue(
      opts.summaryError ? throwError(() => new Error('summary failed')) : of(opts.summary ?? summary()),
    ),
    getProducts: vi.fn().mockReturnValue(
      opts.productsError ? throwError(() => new Error('health list failed')) : of(products),
    ),
    getProduct: vi.fn().mockImplementation((id: string) => {
      if (failDetail.has(id)) {
        return throwError(() => new Error(`detail failed ${id}`));
      }
      return of(details[id] ?? emptyDetail(products.find((p) => p.id === id) ?? product({ id, name: id })));
    }),
  };

  getTestBed().resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [DashboardPageComponent],
    providers: [provideRouter([]), { provide: FmApiService, useValue: api }],
  }).compileComponents();

  const fixture = TestBed.createComponent(DashboardPageComponent);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement, api };
}

function productTiles(el: HTMLElement): HTMLAnchorElement[] {
  return Array.from(el.querySelectorAll('a.product-cell'));
}

function tileByName(el: HTMLElement, name: string): HTMLAnchorElement | undefined {
  return productTiles(el).find((tile) => {
    const label = tile.querySelector('.product-name')?.textContent?.trim();
    return label === name || tile.textContent?.includes(name);
  });
}

function headingOrder(el: HTMLElement, mapsLabel: string, healthLabel: string): number {
  const headings = Array.from(el.querySelectorAll('h2'));
  const mapsIdx = headings.findIndex((h) => h.textContent?.includes(mapsLabel));
  const healthIdx = headings.findIndex((h) => h.textContent?.includes(healthLabel));
  return mapsIdx === -1 || healthIdx === -1 ? -1 : healthIdx - mapsIdx;
}

function expectedGiftShopPercents(): Record<string, number> {
  const profiles = buildProfiles(GIFT_SHOP_CIS, GIFT_SHOP_EVENTS);
  return Object.fromEntries(GIFT_SHOP_CIS.map((item) => [item.fqdn, profiles[item.id]?.healthPercent ?? 100]));
}

describe('Dashboard product health tiles', () => {
  it('stacks tiles below maps and shows Gift Shop summary card plus CI table, not slots', async () => {
    const { el, api } = await setup();

    expect(api.getProducts).toHaveBeenCalled();
    expect(api.getProduct).toHaveBeenCalledWith(GIFT_SHOP_ID);
    expect(productTiles(el)).toHaveLength(6);
    expect(headingOrder(el, 'Карты событий', 'Здоровье продуктов')).toBeGreaterThan(0);

    const mapsSection = el.querySelector('.maps-section');
    const healthSection = el.querySelector('.health-section');
    expect(mapsSection).toBeTruthy();
    expect(healthSection).toBeTruthy();
    expect(mapsSection!.compareDocumentPosition(healthSection!) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );

    const gift = tileByName(el, 'Gift Shop');
    expect(gift).toBeTruthy();
    const text = gift!.textContent ?? '';
    expect(text).toContain('Gift Shop');
    expect(gift!.tagName).toBe('A');
    expect(gift!.getAttribute('href')).toBe(`/health/${GIFT_SHOP_ID}`);
    expect(gift!.getAttribute('href')).not.toBe('/health');

    const warningLabel = HEALTH_LABELS[percentToLevel(75)];
    expect(warningLabel).toBe('Предупреждение');
    expect(gift!.querySelector('.health-score')?.textContent).toMatch(/75\s*%/);
    expect(gift!.querySelector('.severity')?.textContent?.trim()).toBe(warningLabel);
    expect(gift!.querySelector('.damage-label')?.textContent?.trim()).toBe('Анализ урона');
    expect(gift!.querySelector('.damage-value')?.textContent?.trim()).toBe('−25%');
    expect(text).toMatch(/Мин\s+75%/);
    expect(text).toMatch(/Макс\s+75%/);
    expect(gift!.querySelector('.rca-btn')).toBeNull();
    expect(gift!.querySelector('app-monq-health-graph')).toBeNull();
    expect(gift!.querySelector('app-sankey-diagram')).toBeNull();

    const headers = Array.from(gift!.querySelectorAll('.ci-table th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Название КЕ', 'Тип', 'Здоровье']);

    const ciRows = Array.from(gift!.querySelectorAll('.product-ci'));
    expect(ciRows).toHaveLength(4);
    const percents = expectedGiftShopPercents();
    for (const item of GIFT_SHOP_CIS) {
      const row = ciRows.find((r) => r.textContent?.includes(item.fqdn));
      expect(row, `missing CI ${item.fqdn}`).toBeTruthy();
      expect(row!.textContent).toMatch(new RegExp(`${percents[item.fqdn]}\\s*%`));
      expect(row!.textContent).toContain(item.ciType);
      expect(row!.textContent).not.toContain(item.id);
    }
    expect(text).toContain('giftshop-postgres.demo');
    expect(text).toContain('database');
    expect(text).toContain('service');

    for (const code of SLOT_CODES) {
      const labeledAsSlot = ciRows.some((row) => {
        const label = row.querySelector('.ci-label')?.textContent?.trim() ?? '';
        return label === code || label.startsWith(`${code} `);
      });
      expect(labeledAsSlot).toBe(false);
      expect(headers).not.toContain(code);
    }

    expect(text).not.toContain('42 событий');
    expect(text).not.toMatch(/ciIds/i);

    for (const previewName of [1, 2, 3, 4, 5].map((i) => `Preview Only ${i}`)) {
      expect(tileByName(el, previewName)).toBeUndefined();
      expect(el.textContent).not.toContain(previewName);
    }

    const footer = el.querySelector('p.total');
    expect(footer?.textContent).toMatch(/Всего продуктов:\s*6/);
    expect(el.textContent).not.toContain('Всего активных');
    expect(footer?.textContent).not.toContain('10');
  });

  it('keeps the heatmap header link and empty or missing-snapshot tiles as the API returns them', async () => {
    const empty = await setup({ products: [] });
    const heading = Array.from(empty.el.querySelectorAll('h2')).find((h) =>
      h.textContent?.includes('Здоровье продуктов'),
    );
    expect(heading).toBeTruthy();
    const headerLink = heading!.querySelector('a');
    expect(headerLink).toBeTruthy();
    expect(headerLink!.getAttribute('href')).toBe('/health');
    expect(productTiles(empty.el)).toHaveLength(0);
    expect(empty.el.querySelector('p.total')?.textContent).toMatch(/Всего продуктов:\s*0/);
    expect(empty.el.textContent).not.toContain('Всего активных');
    expect(empty.api.getProduct).not.toHaveBeenCalled();

    const missing = await setup({
      products: [
        product({
          id: MISSING_SNAPSHOT_ID,
          name: 'No Snapshot',
          healthPercent: 100,
          components: [],
        }),
      ],
      details: {
        [MISSING_SNAPSHOT_ID]: emptyDetail(
          product({ id: MISSING_SNAPSHOT_ID, name: 'No Snapshot', healthPercent: 100 }),
        ),
      },
    });
    const tile = tileByName(missing.el, 'No Snapshot');
    expect(tile).toBeTruthy();
    expect(tile!.textContent).toMatch(/100\s*%/);
    expect(tile!.querySelectorAll('.product-ci')).toHaveLength(0);
    for (const code of SLOT_CODES) {
      expect(tile!.textContent).not.toContain(code);
    }
    expect(tile!.textContent).not.toContain('giftshop-storefront.demo');
    expect(tile!.getAttribute('href')).toBe(`/health/${MISSING_SNAPSHOT_ID}`);

    const giftSetup = await setup();
    const giftTile = tileByName(giftSetup.el, 'Gift Shop');
    expect(giftTile!.getAttribute('href')).toBe(`/health/${GIFT_SHOP_ID}`);
  });

  it('keeps priority counters and event maps on getDashboardSummary and does not bind tiles to productPreview', async () => {
    const { el, api } = await setup();

    expect(api.getDashboardSummary).toHaveBeenCalled();
    expect(api.getProducts).toHaveBeenCalled();
    expect(api.getProduct).toHaveBeenCalled();
    expect(api).not.toHaveProperty('getProductHealthTiles');
    expect(api).not.toHaveProperty('getCi');

    const cards = Array.from(el.querySelectorAll('a.severity-card'));
    expect(cards).toHaveLength(4);
    const labels = cards.map((c) => c.textContent ?? '');
    expect(labels.some((t) => t.includes('Critical') && t.includes('3'))).toBe(true);
    expect(labels.some((t) => t.includes('Major') && t.includes('2'))).toBe(true);
    expect(labels.some((t) => t.includes('Minor') && t.includes('1'))).toBe(true);
    expect(labels.some((t) => t.includes('Warning') && t.includes('4'))).toBe(true);

    const hrefs = cards.map((c) => c.getAttribute('href') ?? '');
    expect(hrefs.some((h) => h.includes('/console') && h.includes('severity=critical'))).toBe(true);
    expect(hrefs.some((h) => h.includes('severity=major'))).toBe(true);
    expect(hrefs.some((h) => h.includes('severity=minor'))).toBe(true);
    expect(hrefs.some((h) => h.includes('severity=warning'))).toBe(true);

    const mapsHeading = Array.from(el.querySelectorAll('h2')).find((h) =>
      h.textContent?.includes('Карты событий'),
    );
    expect(mapsHeading).toBeTruthy();
    const mapItems = Array.from(el.querySelectorAll('a.map-item')).map((a) => a.textContent?.trim());
    expect(mapItems).toEqual(['Активные', 'Закрытые']);
    expect(headingOrder(el, 'Карты событий', 'Здоровье продуктов')).toBeGreaterThan(0);

    expect(el.textContent).not.toContain('Preview Only');
    expect(productTiles(el).map((t) => t.querySelector('.product-name')?.textContent?.trim())).toEqual([
      'Gift Shop',
      'Low Health',
      'Catalog',
      'Checkout',
      'Payments',
      'Storefront',
    ]);

    const low = tileByName(el, 'Low Health');
    expect(low).toBeTruthy();
    expect(low!.getAttribute('data-level')).toBe(percentToLevel(25));
    expect(low!.getAttribute('data-severity')).toBeNull();
    expect(percentToLevel(25)).toBe('critical');

    const listFail = await setup({ productsError: true });
    expect(listFail.el.querySelectorAll('a.severity-card')).toHaveLength(4);
    expect(productTiles(listFail.el)).toHaveLength(0);
    expect(listFail.el.textContent).toContain('Critical');
    expect(listFail.el.querySelector('p.total')?.textContent).toMatch(/Всего продуктов:\s*0/);
    expect(listFail.el.textContent).not.toContain('Всего активных');

    const detailFail = await setup({ productDetailErrorIds: [GIFT_SHOP_ID] });
    expect(detailFail.el.querySelectorAll('a.severity-card')).toHaveLength(4);
    const giftOnFail = tileByName(detailFail.el, 'Gift Shop');
    expect(giftOnFail).toBeTruthy();
    expect(giftOnFail!.textContent).toMatch(/75\s*%/);
    expect(giftOnFail!.querySelectorAll('.product-ci')).toHaveLength(0);
    expect(productTiles(detailFail.el)).toHaveLength(6);
  });
});

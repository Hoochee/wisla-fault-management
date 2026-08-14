import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { ProductHealth, ProductHealthDetail } from '../../src/app/core/api/api.models';
import { buildCiHealthProfile, getHealthPercentColor } from '../../src/app/core/health/health-profile.util';
import {
  hasSankeyGraph,
  mapHistoryBuckets,
  mapProductHealth,
  mapProductHealthDetail,
  mapSankey,
} from '../../src/app/core/health/health-snapshot.mapper';

const LIST_ROW: ProductHealth = {
  id: '10000000-0000-4000-8000-000000000001',
  name: 'Gift Shop',
  tenant: 'demo',
  site: 'dc-1',
  maxSeverity: 'critical',
  activeEventCount: 2,
  ciIds: ['ci-1'],
  tags: ['shop'],
  healthPercent: 75,
  damagePercent: 20,
  components: [
    {
      code: 'POWER',
      name: 'Power',
      healthPercent: 50,
      damagePercent: 10,
      weight: 20,
      influenceType: 'critical',
      ciIds: ['ci-1'],
    },
    {
      code: 'CPU',
      name: 'CPU',
      healthPercent: 80,
      damagePercent: 8,
      weight: 40,
      influenceType: 'weighted',
      ciIds: ['ci-2'],
    },
  ],
};

describe('health DTO mapping', () => {
  it('maps list percents and component breakdown', () => {
    const mapped = mapProductHealth(LIST_ROW);
    expect(mapped.healthPercent).toBe(75);
    expect(mapped.damagePercent).toBe(20);
    expect(mapped.components).toHaveLength(2);
    expect(mapped.components[0]).toMatchObject({
      code: 'POWER',
      name: 'Power',
      healthPercent: 50,
      damagePercent: 10,
      weight: 20,
      influenceType: 'critical',
    });
    expect(mapped.maxSeverity).toBe('critical');
    expect(mapped.activeEventCount).toBe(2);
  });

  it('maps detail sankey links with from/to/damage', () => {
    const detail: ProductHealthDetail = {
      ...LIST_ROW,
      sankey: {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
        ],
        links: [{ from: 'product:1', to: 'component:CPU', damage: 20 }],
      },
      minHealthToday: 25,
      maxHealthToday: 90,
    };
    const mapped = mapProductHealthDetail(detail);
    expect(mapped.sankey?.links).toEqual([{ from: 'product:1', to: 'component:CPU', damage: 20 }]);
    expect(mapped.sankey?.nodes[1]).toMatchObject({ id: 'component:CPU', kind: 'component' });
    expect(mapped.minHealthToday).toBe(25);
    expect(mapped.maxHealthToday).toBe(90);
    expect(hasSankeyGraph(mapped.sankey)).toBe(true);
  });

  it('treats missing sankey as empty graph, not a fake topology', () => {
    expect(mapSankey(undefined)).toBeNull();
    expect(mapSankey({ nodes: [], links: [] })).toBeNull();
    expect(hasSankeyGraph(null)).toBe(false);
    const mapped = mapProductHealthDetail({ ...LIST_ROW });
    expect(mapped.sankey).toBeNull();
    expect(hasSankeyGraph(mapped.sankey)).toBe(false);
  });

  it('maps history buckets with min/max health', () => {
    const buckets = mapHistoryBuckets([
      {
        bucketStart: '2026-08-13T10:00:00Z',
        bucketMinutes: 15,
        minHealth: 25,
        maxHealth: 75,
        worstSeverity: 'critical',
      },
    ]);
    expect(buckets).toEqual([
      {
        bucketStart: '2026-08-13T10:00:00Z',
        bucketMinutes: 15,
        minHealth: 25,
        maxHealth: 75,
        worstSeverity: 'critical',
      },
    ]);
    expect(mapHistoryBuckets(undefined)).toEqual([]);
  });
});

describe('health-profile.util fake generation', () => {
  const utilPath = path.resolve(__dirname, '../../src/app/core/health/health-profile.util.ts');
  const utilSrc = readFileSync(utilPath, 'utf8');

  it('does not define defaultComponents or a synthetic timeline builder', () => {
    expect(utilSrc).not.toMatch(/function\s+defaultComponents/);
    expect(utilSrc).not.toMatch(/function\s+buildTimeline/);
    expect(utilSrc).not.toContain('CPU / Load');
    expect(utilSrc).not.toContain('Дисковая подсистема');
  });

  it('does not invent CPU/HDD component rows or a synthetic timeline', () => {
    const profile = buildCiHealthProfile(
      {
        id: 'ci-1',
        fqdn: 'host.example',
        ciType: 'server',
        system: 'shop',
        subsystem: 'checkout',
        software: 'java',
        products: ['Gift Shop'],
        tags: [],
      },
      [],
    );
    expect(profile.components).toEqual([]);
    expect(profile.timeline).toEqual([]);
    expect(profile.components.some((c) => /CPU|HDD|диск/i.test(c.name))).toBe(false);
  });

  it('colors heatmap cells from healthPercent', () => {
    expect(getHealthPercentColor(25)).not.toBe(getHealthPercentColor(100));
    expect(getHealthPercentColor(25)).toMatch(/rgba\(/);
  });
});

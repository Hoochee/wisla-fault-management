import { describe, expect, it } from 'vitest';
import {
  layoutSankey,
  looksLikeUuid,
  orientSankeyGraph,
} from '../../src/app/core/health/sankey-layout';

describe('layoutSankey', () => {
  it('places product left of components with readable node labels', () => {
    const layout = layoutSankey(
      {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:POWER', label: 'POWER', kind: 'component' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
        ],
        links: [
          { from: 'product:1', to: 'component:POWER', damage: 19 },
          { from: 'product:1', to: 'component:CPU', damage: 12 },
        ],
      },
      640,
      240,
    );
    expect(layout).not.toBeNull();
    const product = layout!.nodes.find((n) => n.kind === 'product');
    const power = layout!.nodes.find((n) => n.id === 'component:POWER');
    expect(product?.label).toBe('Gift Shop');
    expect(power?.label).toBe('POWER');
    expect(product!.x0).toBeLessThan(power!.x0);
    expect(layout!.nodes.every((n) => !looksLikeUuid(n.label))).toBe(true);
  });

  it('attaches the product node at x≈0 and builds filled Bezier ribbon areas', () => {
    const layout = layoutSankey(
      {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:POWER', label: 'POWER', kind: 'component' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
        ],
        links: [
          { from: 'product:1', to: 'component:POWER', damage: 19 },
          { from: 'product:1', to: 'component:CPU', damage: 12 },
        ],
      },
      640,
      240,
    );
    expect(layout).not.toBeNull();
    const product = layout!.nodes.find((n) => n.kind === 'product');
    expect(product!.x0).toBeLessThan(8);
    expect(product!.x0).toBe(0);
    expect(product!.x1).toBeLessThanOrEqual(4);
    for (const link of layout!.links) {
      expect(link.path).toContain('C');
      expect(link.path).toContain('Z');
      expect(link.path.startsWith('M')).toBe(true);
    }
  });

  it('keeps the product origin compact instead of stretching to plot height', () => {
    const height = 240;
    const layout = layoutSankey(
      {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:POWER', label: 'POWER', kind: 'component' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
          { id: 'component:HDD', label: 'HDD', kind: 'component' },
          { id: 'component:RAM', label: 'RAM', kind: 'component' },
        ],
        links: [
          { from: 'product:1', to: 'component:POWER', damage: 19 },
          { from: 'product:1', to: 'component:CPU', damage: 12 },
          { from: 'product:1', to: 'component:HDD', damage: 0 },
          { from: 'product:1', to: 'component:RAM', damage: 0 },
        ],
      },
      640,
      height,
    );
    expect(layout).not.toBeNull();
    const product = layout!.nodes.find((n) => n.kind === 'product');
    expect(product!.y1 - product!.y0).toBeLessThan(height * 0.55);
    expect(layout!.nodes.every((n) => n.kind !== 'ci')).toBe(true);
  });

  it('scales ribbon width from damage', () => {
    const layout = layoutSankey(
      {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:POWER', label: 'POWER', kind: 'component' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
        ],
        links: [
          { from: 'product:1', to: 'component:POWER', damage: 40 },
          { from: 'product:1', to: 'component:CPU', damage: 10 },
        ],
      },
      640,
      240,
    );
    const wide = layout!.links.find((l) => l.to === 'component:POWER');
    const thin = layout!.links.find((l) => l.to === 'component:CPU');
    expect(wide?.damage).toBe(40);
    expect(thin?.damage).toBe(10);
    expect(wide!.width).toBeGreaterThan(thin!.width * 2);
  });

  it('rewrites legacy CI→component→product graphs and drops UUID CI nodes', () => {
    const ciId = 'c9737f8b-9dec-47c8-b068-bab36a121ea5';
    const oriented = orientSankeyGraph({
      nodes: [
        { id: `ci:${ciId}`, label: ciId, kind: 'ci' },
        { id: 'component:COMMON', label: 'COMMON', kind: 'component' },
        { id: 'product:1', label: 'E2E Product msrihpd9', kind: 'product' },
      ],
      links: [
        { from: `ci:${ciId}`, to: 'component:COMMON', damage: 12 },
        { from: 'component:COMMON', to: 'product:1', damage: 12 },
      ],
    });
    expect(oriented.nodes.map((n) => n.label)).toEqual(
      expect.arrayContaining(['E2E Product msrihpd9', 'COMMON']),
    );
    expect(oriented.nodes.some((n) => n.kind === 'ci')).toBe(false);
    expect(oriented.links).toEqual([
      { from: 'product:1', to: 'component:COMMON', damage: 12 },
    ]);

    const layout = layoutSankey(
      {
        nodes: [
          { id: `ci:${ciId}`, label: ciId, kind: 'ci' },
          { id: 'component:COMMON', label: 'COMMON', kind: 'component' },
          { id: 'product:1', label: 'E2E Product msrihpd9', kind: 'product' },
        ],
        links: [
          { from: `ci:${ciId}`, to: 'component:COMMON', damage: 12 },
          { from: 'component:COMMON', to: 'product:1', damage: 12 },
        ],
      },
      640,
      240,
    );
    expect(layout?.nodes.map((n) => n.label)).toEqual(
      expect.arrayContaining(['E2E Product msrihpd9', 'COMMON']),
    );
    expect(layout?.links[0]).toMatchObject({
      from: 'product:1',
      to: 'component:COMMON',
      fromLabel: 'E2E Product msrihpd9',
      toLabel: 'COMMON',
      damage: 12,
    });
    expect(layout!.links[0].width).toBeGreaterThan(0);
    expect(layout!.links[0].path.length).toBeGreaterThan(0);
  });

  it('returns null when graph is missing', () => {
    expect(layoutSankey({ nodes: [], links: [] }, 400, 200)).toBeNull();
  });

  it('colors ribbons by component code so similar damage stays distinct', () => {
    const layout = layoutSankey(
      {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:POWER', label: 'POWER', kind: 'component' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
          { id: 'component:RAM', label: 'RAM', kind: 'component' },
        ],
        links: [
          { from: 'product:1', to: 'component:POWER', damage: 19 },
          { from: 'product:1', to: 'component:CPU', damage: 12 },
          { from: 'product:1', to: 'component:RAM', damage: 0 },
        ],
      },
      640,
      240,
    );
    expect(layout).not.toBeNull();
    expect(layout!.links.length).toBeGreaterThan(0);
    const power = layout!.links.find((l) => l.to === 'component:POWER');
    const cpu = layout!.links.find((l) => l.to === 'component:CPU');
    const ram = layout!.links.find((l) => l.to === 'component:RAM');
    expect(power?.color).toBe('#e67e22');
    expect(cpu?.color).toBe('#c0392b');
    expect(ram?.color).toBe('#f1c40f');
    expect(power?.color).not.toBe(cpu?.color);
    expect(power?.ribbonLabel).toBe('−19% POWER');
    expect(ram?.ribbonLabel).toBe('RAM');
    expect(ram?.ribbonLabel).not.toMatch(/−0%/);
  });

  it('gives every link a unique color in a multi-component graph', () => {
    const layout = layoutSankey(
      {
        nodes: [
          { id: 'product:1', label: 'Gift Shop', kind: 'product' },
          { id: 'component:POWER', label: 'POWER', kind: 'component' },
          { id: 'component:CPU', label: 'CPU', kind: 'component' },
          { id: 'component:HDD', label: 'HDD', kind: 'component' },
          { id: 'component:RAM', label: 'RAM', kind: 'component' },
        ],
        links: [
          { from: 'product:1', to: 'component:POWER', damage: 19 },
          { from: 'product:1', to: 'component:CPU', damage: 12 },
          { from: 'product:1', to: 'component:HDD', damage: 0 },
          { from: 'product:1', to: 'component:RAM', damage: 0 },
        ],
      },
      640,
      240,
    );
    expect(layout).not.toBeNull();
    const colors = layout!.links.map((l) => l.color);
    expect(colors.length).toBeGreaterThanOrEqual(3);
    expect(new Set(colors).size).toBe(colors.length);
  });
});

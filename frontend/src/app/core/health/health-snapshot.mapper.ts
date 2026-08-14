import {
  ProductHealth,
  ProductHealthDetail,
  ProductHealthHistoryBucket,
  SankeyGraph,
} from '../api/api.models';

export function mapProductHealth(raw: ProductHealth): ProductHealth {
  return {
    ...raw,
    healthPercent: raw.healthPercent,
    damagePercent: raw.damagePercent,
    components: (raw.components ?? []).map((c) => ({
      code: c.code,
      name: c.name,
      healthPercent: c.healthPercent,
      damagePercent: c.damagePercent,
      weight: c.weight,
      influenceType: c.influenceType,
      ciIds: c.ciIds ?? [],
    })),
  };
}

export function mapSankey(sankey?: SankeyGraph | null): SankeyGraph | null {
  if (!sankey || !Array.isArray(sankey.nodes) || !Array.isArray(sankey.links)) {
    return null;
  }
  if (sankey.nodes.length === 0 || sankey.links.length === 0) {
    return null;
  }
  return {
    nodes: sankey.nodes.map((n) => ({ id: n.id, label: n.label, kind: n.kind })),
    links: sankey.links.map((l) => ({ from: l.from, to: l.to, damage: l.damage })),
  };
}

export function hasSankeyGraph(sankey?: SankeyGraph | null): boolean {
  return !!sankey && sankey.nodes.length > 0 && sankey.links.length > 0;
}

export function mapProductHealthDetail(raw: ProductHealthDetail): ProductHealthDetail {
  const base = mapProductHealth(raw);
  return {
    ...raw,
    ...base,
    sankey: mapSankey(raw.sankey),
    minHealthToday: raw.minHealthToday,
    maxHealthToday: raw.maxHealthToday,
    calculatedAt: raw.calculatedAt,
  };
}

export function mapHistoryBuckets(
  raw?: ProductHealthHistoryBucket[] | null,
): ProductHealthHistoryBucket[] {
  if (!raw) return [];
  return raw.map((b) => ({
    bucketStart: b.bucketStart,
    bucketMinutes: b.bucketMinutes,
    minHealth: b.minHealth,
    maxHealth: b.maxHealth,
    worstSeverity: b.worstSeverity,
  }));
}

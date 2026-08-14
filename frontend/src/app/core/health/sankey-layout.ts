import { sankey } from 'd3-sankey';
import { SankeyGraph, SankeyLink, SankeyNode } from '../api/api.models';

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Compact origin bar (Monq). Ribbons use ribbonColorForComponent. */
export const SANKEY_RIBBON = '#e8b48a';

const COMPONENT_RIBBON: Array<{ test: (code: string) => boolean; color: string }> = [
  { test: (c) => c.includes('POWER'), color: '#e67e22' },
  { test: (c) => c.includes('CPU'), color: '#c0392b' },
  { test: (c) => c.includes('HDD') || c.includes('DISK') || c.includes('STORAGE'), color: '#e07a7a' },
  { test: (c) => c.includes('RAM') || c.includes('MEM'), color: '#f1c40f' },
  { test: (c) => c.includes('COMMON'), color: '#27ae60' },
  { test: (c) => c.includes('AVAILABILITY'), color: '#8e44ad' },
];

const FALLBACK_RIBBON = [
  '#2980b9',
  '#16a085',
  '#d35400',
  '#2c3e50',
  '#e67e22',
  '#c0392b',
  '#e07a7a',
  '#f1c40f',
  '#27ae60',
  '#8e44ad',
] as const;

export interface SankeyLayoutNode {
  id: string;
  label: string;
  kind: string;
  code: string;
  damage: number;
  x0: number;
  x1: number;
  y0: number;
  y1: number;
}

export interface SankeyLayoutLink {
  from: string;
  to: string;
  fromLabel: string;
  toLabel: string;
  damage: number;
  path: string;
  width: number;
  color: string;
  ribbonLabel: string;
  midX: number;
  midY: number;
}

export interface SankeyLayout {
  nodes: SankeyLayoutNode[];
  links: SankeyLayoutLink[];
}

export function looksLikeUuid(value: string | undefined | null): boolean {
  return !!value && UUID_RE.test(value);
}

export function ribbonColorForComponent(code: string, index: number): string {
  const c = (code ?? '').toUpperCase();
  for (const { test, color } of COMPONENT_RIBBON) {
    if (test(c)) {
      return color;
    }
  }
  const i = Math.abs(index) % FALLBACK_RIBBON.length;
  return FALLBACK_RIBBON[i];
}

function uniqueRibbonColor(code: string, index: number, used: Set<string>): string {
  const preferred = ribbonColorForComponent(code, index);
  if (!used.has(preferred)) {
    used.add(preferred);
    return preferred;
  }
  for (let offset = 0; offset < FALLBACK_RIBBON.length; offset++) {
    const candidate = FALLBACK_RIBBON[(index + offset) % FALLBACK_RIBBON.length];
    if (!used.has(candidate)) {
      used.add(candidate);
      return candidate;
    }
  }
  used.add(preferred);
  return preferred;
}

export function formatRibbonLabel(componentLabel: string, damage: number): string {
  const name = (componentLabel ?? '').replace(/^:/, '').trim();
  if (damage > 0) {
    const n = Number.isInteger(damage) ? String(damage) : damage.toFixed(2);
    return `−${n}% ${name}`;
  }
  return name;
}

/** Product → components (→ CI fqdn). Drops CI nodes labeled with raw UUIDs. */
export function orientSankeyGraph(graph: SankeyGraph): SankeyGraph {
  const nodes: SankeyNode[] = [];
  for (const node of graph.nodes) {
    if (node.kind === 'ci') {
      const label = node.label?.trim();
      if (!label || looksLikeUuid(label) || looksLikeUuid(stripPrefix(node.id))) {
        continue;
      }
      nodes.push({ ...node, label });
      continue;
    }
    const label = readableLabel(node);
    if (looksLikeUuid(label)) {
      continue;
    }
    nodes.push({ ...node, label });
  }
  const keep = new Set(nodes.map((n) => n.id));
  const productIds = new Set(nodes.filter((n) => n.kind === 'product').map((n) => n.id));
  const ciIds = new Set(nodes.filter((n) => n.kind === 'ci').map((n) => n.id));
  const links: SankeyLink[] = [];
  for (const link of graph.links) {
    let from = link.from;
    let to = link.to;
    if (productIds.has(to) && !productIds.has(from)) {
      [from, to] = [to, from];
    } else if (ciIds.has(from) && !ciIds.has(to)) {
      [from, to] = [to, from];
    }
    if (!keep.has(from) || !keep.has(to) || from === to) {
      continue;
    }
    links.push({ from, to, damage: link.damage });
  }
  return { nodes, links };
}

export function layoutSankey(
  graph: SankeyGraph,
  width: number,
  height: number,
): SankeyLayout | null {
  const oriented = dropCiLayer(orientSankeyGraph(graph));
  if (!oriented.nodes.length || !oriented.links.length) {
    return null;
  }
  const order = new Map(oriented.nodes.map((n, i) => [n.id, i]));
  const byKind = new Map(oriented.nodes.map((n) => [n.id, n]));
  try {
    const layout = sankey<{ id: string; label: string; kind: string }, { damage: number }>()
      .nodeId((d) => d.id)
      .nodeWidth(4)
      .nodePadding(44)
      .nodeSort((a, b) => (order.get(a.id) ?? 0) - (order.get(b.id) ?? 0))
      .extent([
        [0, 16],
        [width - 64, height - 16],
      ]);
    const computed = layout({
      nodes: oriented.nodes.map((n) => ({ ...n })),
      links: oriented.links.map((l) => ({
        source: l.from,
        target: l.to,
        value: Math.max(l.damage, 0.01),
        damage: l.damage,
      })),
    });
    pinProductOrigin(computed.nodes ?? []);
    centerGraphVertically(computed.nodes ?? [], computed.links ?? [], height);
    const nodes: SankeyLayoutNode[] = (computed.nodes ?? []).map((n) => ({
      id: n.id,
      label: n.label,
      kind: n.kind,
      code: componentCode(n.id, n.label),
      damage: 0,
      x0: n.x0 ?? 0,
      x1: n.x1 ?? 0,
      y0: n.y0 ?? 0,
      y1: n.y1 ?? 0,
    }));
    const byId = new Map(nodes.map((n) => [n.id, n]));
    const usedRibbonColors = new Set<string>();
    const links: SankeyLayoutLink[] = (computed.links ?? []).map((l, index) => {
      const source = typeof l.source === 'object' ? l.source.id : String(l.source);
      const target = typeof l.target === 'object' ? l.target.id : String(l.target);
      const sourceNode = typeof l.source === 'object' ? l.source : undefined;
      const targetNode = typeof l.target === 'object' ? l.target : undefined;
      const fromLabel = byId.get(source)?.label ?? source;
      const toLabel = byId.get(target)?.label ?? target;
      const fromKind = byKind.get(source)?.kind ?? byId.get(source)?.kind ?? '';
      const toKind = byKind.get(target)?.kind ?? byId.get(target)?.kind ?? '';
      const sx = sourceNode?.x1 ?? 0;
      const tx = targetNode?.x0 ?? 0;
      const damage = l.damage;
      const width = Math.max(l.width ?? 1, damage > 0 ? 4 : 3);
      // d3-sankey 0.12 stores y0/y1 as the band *center*; convert to tops for the area path.
      const syTop = (l.y0 ?? 0) - width / 2;
      const tyTop = (l.y1 ?? 0) - width / 2;
      const componentLabel =
        toKind === 'component' ? toLabel : fromKind === 'component' ? fromLabel : toLabel;
      const colorNode = toKind === 'component' ? byId.get(target) : byId.get(source);
      const colorCode = colorNode?.code ?? componentLabel;
      return {
        from: source,
        to: target,
        fromLabel,
        toLabel,
        damage,
        path: ribbonAreaPath(sx, syTop, tx, tyTop, width),
        width,
        color: uniqueRibbonColor(colorCode, index, usedRibbonColors),
        ribbonLabel: formatRibbonLabel(componentLabel, damage),
        midX: (sx + tx) / 2,
        midY: ((l.y0 ?? 0) + (l.y1 ?? 0)) / 2,
      };
    });
    for (const link of links) {
      const target = byId.get(link.to);
      if (target && target.kind !== 'product') {
        target.damage += link.damage;
      }
    }
    return { nodes, links };
  } catch {
    return null;
  }
}

/** Closed cubic-Bezier ribbon (top edge + bottom edge), not a stroked centerline. */
export function ribbonAreaPath(
  sx: number,
  syTop: number,
  tx: number,
  tyTop: number,
  width: number,
): string {
  const sy1 = syTop + width;
  const ty1 = tyTop + width;
  const c = sx + (tx - sx) * 0.55;
  return `M${sx},${syTop}C${c},${syTop} ${c},${tyTop} ${tx},${tyTop}L${tx},${ty1}C${c},${ty1} ${c},${sy1} ${sx},${sy1}Z`;
}

/** Product → component only (Monq terminals, no CI column). */
function dropCiLayer(graph: SankeyGraph): SankeyGraph {
  const nodes = graph.nodes.filter((n) => n.kind !== 'ci');
  const keep = new Set(nodes.map((n) => n.id));
  const links = graph.links.filter((l) => keep.has(l.from) && keep.has(l.to));
  return { nodes, links };
}

interface SankeyOriginNode {
  kind: string;
  x0?: number;
  x1?: number;
  y0?: number;
  y1?: number;
}

/** Pin the product node to the left edge; keep natural (compact) d3-sankey height. */
function pinProductOrigin(nodes: SankeyOriginNode[]): void {
  const product = nodes.find((n) => n.kind === 'product');
  if (!product) {
    return;
  }
  product.x0 = 0;
  product.x1 = 2;
}

interface SankeyYBox {
  y0?: number;
  y1?: number;
}

/** Uniform vertical shift so the compact bundle sits in the middle of the SVG. */
function centerGraphVertically(nodes: SankeyYBox[], links: SankeyYBox[], height: number): void {
  let minY = Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    minY = Math.min(minY, n.y0 ?? 0);
    maxY = Math.max(maxY, n.y1 ?? 0);
  }
  if (!Number.isFinite(minY) || !Number.isFinite(maxY)) {
    return;
  }
  const dy = (height - (maxY - minY)) / 2 - minY;
  if (Math.abs(dy) < 0.5) {
    return;
  }
  for (const n of nodes) {
    n.y0 = (n.y0 ?? 0) + dy;
    n.y1 = (n.y1 ?? 0) + dy;
  }
  for (const l of links) {
    l.y0 = (l.y0 ?? 0) + dy;
    l.y1 = (l.y1 ?? 0) + dy;
  }
}

function stripPrefix(id: string): string {
  const i = id.indexOf(':');
  return i >= 0 ? id.slice(i + 1) : id;
}

function componentCode(id: string, label: string): string {
  const fromId = stripPrefix(id);
  if (fromId && !looksLikeUuid(fromId)) {
    return fromId;
  }
  return label;
}

function readableLabel(node: SankeyNode): string {
  const label = node.label?.trim();
  if (label && !looksLikeUuid(label)) {
    return label;
  }
  const suffix = stripPrefix(node.id);
  if (suffix && !looksLikeUuid(suffix)) {
    return suffix;
  }
  return label || node.id;
}

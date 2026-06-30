import { RuleCanvas, RuleType } from '../../core/api/api.models';
import {
  RuleBlock,
  RuleConnection,
  defaultBlocks,
  defaultConnections,
} from './rule-canvas.models';

export function canvasToEditor(canvas?: RuleCanvas | null): {
  blocks: RuleBlock[];
  connections: RuleConnection[];
} {
  if (!canvas?.nodes?.length) {
    return { blocks: [...defaultBlocks], connections: [...defaultConnections] };
  }

  const blocks: RuleBlock[] = canvas.nodes.map((node) => {
    const data = (node.data ?? {}) as Record<string, string>;
    const { label, ...config } = data;
    return {
      id: node.id,
      type: node.type as RuleBlock['type'],
      label: label ?? node.type,
      x: node.position?.x ?? 0,
      y: node.position?.y ?? 0,
      config,
    };
  });

  const connections: RuleConnection[] = (canvas.edges ?? []).map((edge) => ({
    from: edge.source,
    to: edge.target,
  }));

  return { blocks, connections };
}

export function editorToCanvas(blocks: RuleBlock[], connections: RuleConnection[]): RuleCanvas {
  return {
    nodes: blocks.map((block) => ({
      id: block.id,
      type: block.type,
      position: { x: block.x, y: block.y },
      data: { label: block.label, ...block.config },
    })),
    edges: connections.map((c) => ({
      id: `${c.from}-${c.to}`,
      source: c.from,
      target: c.to,
    })),
  };
}

export function triggerTypeFromBlocks(blocks: RuleBlock[]): string {
  const trigger = blocks.find((b) => b.type === 'trigger');
  if (!trigger) return 'Событие потока';
  return trigger.config['triggerType'] === 'fm' ? 'Событие FM' : 'Событие потока';
}

export interface RuleMetaForm {
  name: string;
  description: string;
  ruleType: RuleType;
}

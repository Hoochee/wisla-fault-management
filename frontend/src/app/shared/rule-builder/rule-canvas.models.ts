export type BlockType =
  | 'trigger'
  | 'condition'
  | 'switch'
  | 'dedup'
  | 'threshold'
  | 'correlation'
  | 'notify'
  | 'push'
  | 'status';

export interface RuleBlock {
  id: string;
  type: BlockType;
  label: string;
  x: number;
  y: number;
  config: Record<string, string>;
}

export interface RuleConnection {
  from: string;
  to: string;
}

export const BLOCK_WIDTH = 160;
export const BLOCK_HEIGHT = 52;

export const defaultBlocks: RuleBlock[] = [
  { id: 'b1', type: 'trigger', label: 'Событие потока', x: 200, y: 20, config: { triggerType: 'stream' } },
  { id: 'b2', type: 'condition', label: 'severity = critical', x: 200, y: 100, config: { field: 'severity', operator: 'eq', value: 'critical' } },
  { id: 'b3', type: 'switch', label: 'Switch', x: 200, y: 180, config: {} },
  { id: 'b4', type: 'dedup', label: 'Дедупликация', x: 80, y: 280, config: { key: 'source_id + title + ci_id' } },
  { id: 'b5', type: 'threshold', label: 'Порог: 5 за 10 мин', x: 320, y: 280, config: { count: '5', windowMin: '10' } },
  { id: 'b6', type: 'status', label: 'Смена статуса', x: 200, y: 380, config: { status: 'in_progress' } },
];

export const defaultConnections: RuleConnection[] = [
  { from: 'b1', to: 'b2' },
  { from: 'b2', to: 'b3' },
  { from: 'b3', to: 'b4' },
  { from: 'b3', to: 'b5' },
  { from: 'b4', to: 'b6' },
  { from: 'b5', to: 'b6' },
];

export interface PaletteItem {
  type: BlockType;
  label: string;
  defaultLabel: string;
  defaultConfig: Record<string, string>;
}

export const palette: PaletteItem[] = [
  { type: 'trigger', label: 'Событие потока', defaultLabel: 'Событие потока', defaultConfig: { triggerType: 'stream' } },
  { type: 'trigger', label: 'Событие FM', defaultLabel: 'Событие FM', defaultConfig: { triggerType: 'fm' } },
  { type: 'condition', label: 'Условие', defaultLabel: 'severity = critical', defaultConfig: { field: 'severity', operator: 'eq', value: 'critical' } },
  { type: 'switch', label: 'Switch', defaultLabel: 'Switch', defaultConfig: {} },
  { type: 'dedup', label: 'Дедупликация', defaultLabel: 'Дедупликация', defaultConfig: { key: 'source_id + title + ci_id' } },
  { type: 'threshold', label: 'Порог', defaultLabel: 'Порог: 5 за 10 мин', defaultConfig: { count: '5', windowMin: '10' } },
  {
    type: 'correlation',
    label: 'Корреляция',
    defaultLabel: 'Корреляция: 2 за 15 мин',
    defaultConfig: { count: '2', windowMin: '15', matchField: '' },
  },
  {
    type: 'notify',
    label: 'Оповещение',
    defaultLabel: 'Notify: email',
    defaultConfig: { channel: 'email', emailAddress: '' },
  },
  {
    type: 'push',
    label: 'Push (in-app)',
    defaultLabel: 'Push',
    defaultConfig: { message: '' },
  },
  { type: 'status', label: 'Смена статуса', defaultLabel: 'Смена статуса', defaultConfig: { status: 'in_progress' } },
];

export const typeLabels: Record<BlockType, string> = {
  trigger: 'Триггер',
  condition: 'Условие',
  switch: 'Switch',
  dedup: 'Дедупликация',
  threshold: 'Порог',
  correlation: 'Корреляция',
  notify: 'Оповещение',
  push: 'Push (in-app)',
  status: 'Смена статуса',
};

export const POST_MVP_PALETTE_TYPES: ReadonlySet<BlockType> = new Set(['status']);

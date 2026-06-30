import { useCallback, useRef, useState } from 'react';

export type BlockType = 'trigger' | 'condition' | 'switch' | 'dedup' | 'threshold' | 'status';

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

const blockStyles: Record<BlockType, string> = {
  trigger: 'bg-[#4a9eff]/20 border-[#4a9eff] text-[#4a9eff]',
  condition: 'bg-amber-500/10 border-amber-500/50 text-amber-300',
  switch: 'bg-purple-500/10 border-purple-500/50 text-purple-300',
  dedup: 'bg-green-500/10 border-green-500/50 text-green-300',
  threshold: 'bg-orange-500/10 border-orange-500/50 text-orange-300',
  status: 'bg-blue-500/10 border-blue-500/50 text-blue-300',
};

export const BLOCK_WIDTH = 160;
export const BLOCK_HEIGHT = 52;
const DRAG_THRESHOLD = 4;

function portPos(block: RuleBlock, port: 'in' | 'out') {
  return {
    x: block.x + BLOCK_WIDTH / 2,
    y: port === 'in' ? block.y : block.y + BLOCK_HEIGHT,
  };
}

interface RuleCanvasProps {
  blocks: RuleBlock[];
  connections: RuleConnection[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onMove: (id: string, x: number, y: number) => void;
  onConnect: (from: string, to: string) => void;
}

export function RuleCanvas({ blocks, connections, selectedId, onSelect, onMove, onConnect }: RuleCanvasProps) {
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [linkingFrom, setLinkingFrom] = useState<string | null>(null);
  const [linkCursor, setLinkCursor] = useState<{ x: number; y: number } | null>(null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const dragOffset = useRef({ x: 0, y: 0 });
  const dragStart = useRef({ x: 0, y: 0 });
  const moved = useRef(false);

  const canvasPoint = useCallback((clientX: number, clientY: number) => {
    const rect = canvasRef.current!.getBoundingClientRect();
    return { x: clientX - rect.left, y: clientY - rect.top };
  }, []);

  const handlePointerDown = useCallback((e: React.PointerEvent, blockId: string) => {
    if (e.button !== 0 || linkingFrom) return;
    const block = blocks.find((b) => b.id === blockId);
    const canvas = canvasRef.current;
    if (!block || !canvas) return;

    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);

    const rect = canvas.getBoundingClientRect();
    dragOffset.current = {
      x: e.clientX - rect.left - block.x,
      y: e.clientY - rect.top - block.y,
    };
    dragStart.current = { x: e.clientX, y: e.clientY };
    moved.current = false;
    setDraggingId(blockId);
    onSelect(blockId);
  }, [blocks, linkingFrom, onSelect]);

  const handlePointerMove = useCallback((e: React.PointerEvent) => {
    if (linkingFrom && canvasRef.current) {
      setLinkCursor(canvasPoint(e.clientX, e.clientY));
      return;
    }
    if (!draggingId || !canvasRef.current) return;

    if (Math.abs(e.clientX - dragStart.current.x) > DRAG_THRESHOLD
      || Math.abs(e.clientY - dragStart.current.y) > DRAG_THRESHOLD) {
      moved.current = true;
    }

    const rect = canvasRef.current.getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left - dragOffset.current.x, rect.width - BLOCK_WIDTH));
    const y = Math.max(0, Math.min(e.clientY - rect.top - dragOffset.current.y, rect.height - BLOCK_HEIGHT));
    onMove(draggingId, x, y);
  }, [draggingId, linkingFrom, onMove, canvasPoint]);

  const handlePointerUp = useCallback((e: React.PointerEvent) => {
    if (linkingFrom) return;
    if (!draggingId) return;
    e.currentTarget.releasePointerCapture(e.pointerId);
    if (!moved.current) {
      onSelect(draggingId);
    }
    setDraggingId(null);
  }, [draggingId, linkingFrom, onSelect]);

  const startLink = (e: React.PointerEvent, blockId: string) => {
    e.preventDefault();
    e.stopPropagation();
    canvasRef.current?.setPointerCapture(e.pointerId);
    setLinkingFrom(blockId);
    setLinkCursor(canvasPoint(e.clientX, e.clientY));
    onSelect(blockId);
  };

  const findInputPortTarget = (clientX: number, clientY: number): string | null => {
    const pt = canvasPoint(clientX, clientY);
    for (const block of blocks) {
      const inPort = portPos(block, 'in');
      const dx = pt.x - inPort.x;
      const dy = pt.y - inPort.y;
      if (Math.hypot(dx, dy) <= 14) return block.id;
    }
    return null;
  };

  const handleCanvasPointerUp = (e: React.PointerEvent) => {
    if (!linkingFrom) return;
    const targetId = findInputPortTarget(e.clientX, e.clientY);
    if (targetId && targetId !== linkingFrom) {
      onConnect(linkingFrom, targetId);
    }
    setLinkingFrom(null);
    setLinkCursor(null);
    canvasRef.current?.releasePointerCapture(e.pointerId);
  };

  const linkingFromBlock = linkingFrom ? blocks.find((b) => b.id === linkingFrom) : null;

  return (
    <div
      ref={canvasRef}
      className={`relative bg-[#1a1d23] border rounded-lg overflow-hidden select-none min-h-[480px] ${
        linkingFrom ? 'border-[#4a9eff]/60' : 'border-[#3a3f4b]'
      }`}
      style={{ height: 480 }}
      onPointerDown={() => { if (!linkingFrom) onSelect(null); }}
      onPointerMove={handlePointerMove}
      onPointerUp={handleCanvasPointerUp}
    >
      <svg className="absolute inset-0 w-full h-full pointer-events-none">
        {connections.map((c) => {
          const from = blocks.find((b) => b.id === c.from);
          const to = blocks.find((b) => b.id === c.to);
          if (!from || !to) return null;
          const p1 = portPos(from, 'out');
          const p2 = portPos(to, 'in');
          const midY = (p1.y + p2.y) / 2;
          return (
            <path
              key={`${c.from}-${c.to}`}
              d={`M ${p1.x} ${p1.y} C ${p1.x} ${midY}, ${p2.x} ${midY}, ${p2.x} ${p2.y}`}
              fill="none"
              stroke="#4a9eff"
              strokeWidth="2"
              opacity="0.7"
            />
          );
        })}
        {linkingFromBlock && linkCursor && (() => {
          const p1 = portPos(linkingFromBlock, 'out');
          const midY = (p1.y + linkCursor.y) / 2;
          return (
            <path
              d={`M ${p1.x} ${p1.y} C ${p1.x} ${midY}, ${linkCursor.x} ${midY}, ${linkCursor.x} ${linkCursor.y}`}
              fill="none"
              stroke="#4a9eff"
              strokeWidth="2"
              strokeDasharray="6 4"
            />
          );
        })()}
      </svg>

      {blocks.map((block) => (
        <div
          key={block.id}
          className="absolute"
          style={{ left: block.x, top: block.y, width: BLOCK_WIDTH, height: BLOCK_HEIGHT }}
        >
          {/* Входной порт */}
          <button
            type="button"
            title="Вход — отпустите сюда связь"
            className={`absolute -top-2 left-1/2 -translate-x-1/2 w-4 h-4 rounded-full border-2 z-20 transition-colors pointer-events-none ${
              linkingFrom && linkingFrom !== block.id
                ? 'border-[#4a9eff] bg-[#4a9eff]/30 scale-125'
                : 'border-[#6b7280] bg-[#1a1d23]'
            }`}
          />

          <div
            role="button"
            tabIndex={0}
            className={`w-full h-full px-3 py-2 rounded border text-xs font-medium touch-none ${
              blockStyles[block.type]
            } ${selectedId === block.id ? 'ring-2 ring-[#4a9eff] shadow-lg' : 'hover:ring-1 hover:ring-[#4a9eff]/40'} ${
              draggingId === block.id ? 'cursor-grabbing' : 'cursor-grab'
            }`}
            onPointerDown={(e) => handlePointerDown(e, block.id)}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
          >
            <div className="text-[10px] uppercase opacity-60 mb-0.5">{block.type}</div>
            <div className="truncate">{block.label}</div>
          </div>

          {/* Выходной порт */}
          <button
            type="button"
            title="Выход — потяните к входу другого блока"
            className={`absolute -bottom-2 left-1/2 -translate-x-1/2 w-4 h-4 rounded-full border-2 z-20 transition-colors ${
              linkingFrom === block.id
                ? 'border-[#4a9eff] bg-[#4a9eff] scale-125'
                : 'border-[#6b7280] bg-[#1a1d23] hover:border-[#4a9eff] hover:bg-[#4a9eff]/20 cursor-crosshair'
            }`}
            onPointerDown={(e) => startLink(e, block.id)}
          />
        </div>
      ))}

      <div className="absolute bottom-2 left-2 right-2 flex justify-between text-[10px] text-gray-600 pointer-events-none">
        <span>Кружок снизу → потянуть → кружок сверху другого блока</span>
        {linkingFrom && <span className="text-[#4a9eff]">Режим связи — отпустите на входном порте</span>}
      </div>
    </div>
  );
}

import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
  signal,
} from '@angular/core';
import {
  BLOCK_HEIGHT,
  BLOCK_WIDTH,
  RuleBlock,
  RuleConnection,
} from './rule-canvas.models';

const DRAG_THRESHOLD = 4;

@Component({
  selector: 'app-rule-canvas',
  standalone: true,
  template: `
    <div
      #canvas
      class="canvas"
      [class.linking]="linkingFrom()"
      (pointerdown)="onCanvasPointerDown($event)"
      (pointermove)="onCanvasPointerMove($event)"
      (pointerup)="onCanvasPointerUp($event)"
    >
      <svg class="edges">
        @for (c of connections; track c.from + c.to) {
          @if (edgePath(c); as d) {
            <path [attr.d]="d" fill="none" stroke="var(--accent)" stroke-width="2" opacity="0.7" />
          }
        }
        @if (linkPath(); as d) {
          <path [attr.d]="d" fill="none" stroke="var(--accent)" stroke-width="2" stroke-dasharray="6 4" />
        }
      </svg>

      @for (block of blocks; track block.id) {
        <div class="block-wrap" [style.left.px]="block.x" [style.top.px]="block.y">
          <div
            class="port port-in"
            [class.highlight]="linkingFrom() && linkingFrom() !== block.id"
          ></div>
          <div
            class="block"
            [attr.data-type]="block.type"
            [class.selected]="selectedId === block.id"
            [class.dragging]="draggingId() === block.id"
            (pointerdown)="onBlockPointerDown($event, block.id)"
            (pointermove)="onBlockPointerMove($event)"
            (pointerup)="onBlockPointerUp($event)"
            (pointercancel)="onBlockPointerUp($event)"
          >
            <div class="type">{{ block.type }}</div>
            <div class="label">{{ block.label }}</div>
          </div>
          <button
            type="button"
            class="port port-out"
            [class.active]="linkingFrom() === block.id"
            title="Выход — потяните к входу другого блока"
            (pointerdown)="startLink($event, block.id)"
          ></button>
        </div>
      }

      <div class="hint">
        <span>Кружок снизу → потянуть → кружок сверху другого блока</span>
        @if (linkingFrom()) {
          <span class="link-mode">Режим связи — отпустите на входном порте</span>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .canvas {
        position: relative;
        background: var(--bg-card);
        border: 1px solid var(--border);
        border-radius: 8px;
        overflow: hidden;
        user-select: none;
        min-height: 480px;
        height: 480px;
      }
      .canvas.linking {
        border-color: rgba(47, 111, 237, 0.6);
      }
      .edges {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
      }
      .block-wrap {
        position: absolute;
        width: ${BLOCK_WIDTH}px;
        height: ${BLOCK_HEIGHT}px;
      }
      .block {
        width: 100%;
        height: 100%;
        padding: 0.5rem 0.75rem;
        border-radius: 6px;
        border: 1px solid;
        font-size: 0.75rem;
        font-weight: 500;
        touch-action: none;
        cursor: grab;
      }
      .block.dragging {
        cursor: grabbing;
      }
      .block.selected {
        box-shadow: 0 0 0 2px var(--accent);
      }
      .block[data-type='trigger'] {
        background: rgba(47, 111, 237, 0.12);
        border-color: var(--accent);
        color: var(--accent);
      }
      .block[data-type='condition'] {
        background: rgba(245, 158, 11, 0.1);
        border-color: rgba(245, 158, 11, 0.5);
        color: #b45309;
      }
      .block[data-type='switch'] {
        background: rgba(168, 85, 247, 0.1);
        border-color: rgba(168, 85, 247, 0.5);
        color: #7c3aed;
      }
      .block[data-type='dedup'] {
        background: rgba(34, 197, 94, 0.1);
        border-color: rgba(34, 197, 94, 0.5);
        color: #15803d;
      }
      .block[data-type='threshold'] {
        background: rgba(249, 115, 22, 0.1);
        border-color: rgba(249, 115, 22, 0.5);
        color: #c2410c;
      }
      .block[data-type='correlation'] {
        background: rgba(236, 72, 153, 0.1);
        border-color: rgba(236, 72, 153, 0.5);
        color: #be185d;
      }
      .block[data-type='notify'] {
        background: rgba(20, 184, 166, 0.1);
        border-color: rgba(20, 184, 166, 0.5);
        color: #0f766e;
      }
      .block[data-type='push'] {
        background: rgba(99, 102, 241, 0.1);
        border-color: rgba(99, 102, 241, 0.5);
        color: #4338ca;
      }
      .block[data-type='status'] {
        background: rgba(59, 130, 246, 0.1);
        border-color: rgba(59, 130, 246, 0.5);
        color: #1d4ed8;
      }
      .type {
        font-size: 0.625rem;
        text-transform: uppercase;
        opacity: 0.6;
        margin-bottom: 0.125rem;
      }
      .label {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .port {
        position: absolute;
        left: 50%;
        transform: translateX(-50%);
        width: 1rem;
        height: 1rem;
        border-radius: 50%;
        border: 2px solid var(--text-muted);
        background: var(--bg-card);
        z-index: 2;
        padding: 0;
      }
      .port-in {
        top: -0.5rem;
        pointer-events: none;
      }
      .port-in.highlight {
        border-color: var(--accent);
        background: rgba(47, 111, 237, 0.3);
        transform: translateX(-50%) scale(1.25);
      }
      .port-out {
        bottom: -0.5rem;
        cursor: crosshair;
      }
      .port-out.active {
        border-color: var(--accent);
        background: var(--accent);
        transform: translateX(-50%) scale(1.25);
      }
      .hint {
        position: absolute;
        bottom: 0.5rem;
        left: 0.5rem;
        right: 0.5rem;
        display: flex;
        justify-content: space-between;
        font-size: 0.625rem;
        color: var(--text-muted);
        pointer-events: none;
      }
      .link-mode {
        color: var(--accent);
      }
    `,
  ],
})
export class RuleCanvasComponent {
  @Input({ required: true }) blocks!: RuleBlock[];
  @Input({ required: true }) connections!: RuleConnection[];
  @Input() selectedId: string | null = null;

  @Output() selectedIdChange = new EventEmitter<string | null>();
  @Output() blockMove = new EventEmitter<{ id: string; x: number; y: number }>();
  @Output() connect = new EventEmitter<{ from: string; to: string }>();

  @ViewChild('canvas', { static: true }) canvasRef!: ElementRef<HTMLDivElement>;

  readonly draggingId = signal<string | null>(null);
  readonly linkingFrom = signal<string | null>(null);
  readonly linkCursor = signal<{ x: number; y: number } | null>(null);

  private dragOffset = { x: 0, y: 0 };
  private dragStart = { x: 0, y: 0 };
  private moved = false;
  private activePointerId: number | null = null;

  portPos(block: RuleBlock, port: 'in' | 'out'): { x: number; y: number } {
    return {
      x: block.x + BLOCK_WIDTH / 2,
      y: port === 'in' ? block.y : block.y + BLOCK_HEIGHT,
    };
  }

  edgePath(c: RuleConnection): string | null {
    const from = this.blocks.find((b) => b.id === c.from);
    const to = this.blocks.find((b) => b.id === c.to);
    if (!from || !to) return null;
    const p1 = this.portPos(from, 'out');
    const p2 = this.portPos(to, 'in');
    const midY = (p1.y + p2.y) / 2;
    return `M ${p1.x} ${p1.y} C ${p1.x} ${midY}, ${p2.x} ${midY}, ${p2.x} ${p2.y}`;
  }

  linkPath(): string | null {
    const fromId = this.linkingFrom();
    const cursor = this.linkCursor();
    if (!fromId || !cursor) return null;
    const from = this.blocks.find((b) => b.id === fromId);
    if (!from) return null;
    const p1 = this.portPos(from, 'out');
    const midY = (p1.y + cursor.y) / 2;
    return `M ${p1.x} ${p1.y} C ${p1.x} ${midY}, ${cursor.x} ${midY}, ${cursor.x} ${cursor.y}`;
  }

  onCanvasPointerDown(event: PointerEvent): void {
    if (!this.linkingFrom()) {
      this.selectedIdChange.emit(null);
    }
  }

  onBlockPointerDown(event: PointerEvent, blockId: string): void {
    if (event.button !== 0 || this.linkingFrom()) return;
    const block = this.blocks.find((b) => b.id === blockId);
    const canvas = this.canvasRef.nativeElement;
    if (!block) return;

    event.preventDefault();
    event.stopPropagation();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.activePointerId = event.pointerId;

    const rect = canvas.getBoundingClientRect();
    this.dragOffset = {
      x: event.clientX - rect.left - block.x,
      y: event.clientY - rect.top - block.y,
    };
    this.dragStart = { x: event.clientX, y: event.clientY };
    this.moved = false;
    this.draggingId.set(blockId);
    this.selectedIdChange.emit(blockId);
  }

  onBlockPointerMove(event: PointerEvent): void {
    if (this.linkingFrom()) {
      this.linkCursor.set(this.canvasPoint(event.clientX, event.clientY));
      return;
    }
    const draggingId = this.draggingId();
    if (!draggingId) return;

    if (
      Math.abs(event.clientX - this.dragStart.x) > DRAG_THRESHOLD ||
      Math.abs(event.clientY - this.dragStart.y) > DRAG_THRESHOLD
    ) {
      this.moved = true;
    }

    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const x = Math.max(0, Math.min(event.clientX - rect.left - this.dragOffset.x, rect.width - BLOCK_WIDTH));
    const y = Math.max(0, Math.min(event.clientY - rect.top - this.dragOffset.y, rect.height - BLOCK_HEIGHT));
    this.blockMove.emit({ id: draggingId, x, y });
  }

  onBlockPointerUp(event: PointerEvent): void {
    if (this.linkingFrom()) return;
    const draggingId = this.draggingId();
    if (!draggingId) return;
    try {
      (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
    } catch {
      /* ignore */
    }
    if (!this.moved) {
      this.selectedIdChange.emit(draggingId);
    }
    this.draggingId.set(null);
    this.activePointerId = null;
  }

  onCanvasPointerMove(event: PointerEvent): void {
    if (this.linkingFrom()) {
      this.linkCursor.set(this.canvasPoint(event.clientX, event.clientY));
      return;
    }
    if (this.draggingId()) {
      this.onBlockPointerMove(event);
    }
  }

  startLink(event: PointerEvent, blockId: string): void {
    event.preventDefault();
    event.stopPropagation();
    this.canvasRef.nativeElement.setPointerCapture(event.pointerId);
    this.linkingFrom.set(blockId);
    this.linkCursor.set(this.canvasPoint(event.clientX, event.clientY));
    this.selectedIdChange.emit(blockId);
  }

  onCanvasPointerUp(event: PointerEvent): void {
    const fromId = this.linkingFrom();
    if (!fromId) return;

    const targetId = this.findInputPortTarget(event.clientX, event.clientY);
    if (targetId && targetId !== fromId) {
      this.connect.emit({ from: fromId, to: targetId });
    }
    this.linkingFrom.set(null);
    this.linkCursor.set(null);
    try {
      this.canvasRef.nativeElement.releasePointerCapture(event.pointerId);
    } catch {
      /* ignore */
    }
  }

  private canvasPoint(clientX: number, clientY: number): { x: number; y: number } {
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    return { x: clientX - rect.left, y: clientY - rect.top };
  }

  private findInputPortTarget(clientX: number, clientY: number): string | null {
    const pt = this.canvasPoint(clientX, clientY);
    for (const block of this.blocks) {
      const inPort = this.portPos(block, 'in');
      const dx = pt.x - inPort.x;
      const dy = pt.y - inPort.y;
      if (Math.hypot(dx, dy) <= 14) return block.id;
    }
    return null;
  }
}

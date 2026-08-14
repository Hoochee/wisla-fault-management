import { Component, Input } from '@angular/core';
import { SankeyGraph } from '../../core/api/api.models';
import {
  layoutSankey,
  SANKEY_RIBBON,
  SankeyLayout,
  SankeyLayoutLink,
  SankeyLayoutNode,
} from '../../core/health/sankey-layout';

type IconKind = 'power' | 'cpu' | 'hdd' | 'ram' | 'cloud';

@Component({
  selector: 'app-sankey-diagram',
  standalone: true,
  template: `
    @if (!layout) {
      <p class="empty">Нет данных графа здоровья</p>
    } @else {
      <svg class="sankey" [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img" aria-label="Граф здоровья">
        <defs>
          @for (link of layout.links; track link.from + '>' + link.to) {
            <linearGradient
              [attr.id]="ribbonGradientId(link)"
              gradientUnits="objectBoundingBox"
              x1="0"
              x2="1"
              y1="0"
              y2="0"
            >
              <stop offset="0%" [attr.stop-color]="link.color" stop-opacity="0.92" />
              <stop offset="100%" [attr.stop-color]="link.color" stop-opacity="0.22" />
            </linearGradient>
          }
        </defs>
        @for (link of layout.links; track link.from + '>' + link.to) {
          <path
            [attr.d]="link.path"
            [attr.fill]="'url(#' + ribbonGradientId(link) + ')'"
            stroke="none"
            class="ribbon"
            [attr.data-damage]="link.damage"
          >
            <title>{{ link.ribbonLabel }}</title>
          </path>
          <text
            class="ribbon-label"
            [attr.x]="link.midX"
            [attr.y]="link.midY"
            text-anchor="middle"
          >
            {{ link.ribbonLabel }}
          </text>
        }
        @if (origin; as product) {
          <rect
            class="origin-bar"
            [attr.x]="product.x0"
            [attr.y]="product.y0"
            width="6"
            [attr.height]="product.y1 - product.y0"
            rx="3"
            ry="3"
            [attr.fill]="originFill"
          />
        }
        @for (node of terminals; track node.id) {
          <g
            class="terminal"
            [attr.data-kind]="node.kind"
            [attr.data-code]="node.code"
            [attr.transform]="'translate(' + terminalX(node) + ',' + terminalY(node) + ')'"
          >
            <title>{{ node.label }}</title>
            <circle class="icon-ring" r="16" />
            <g class="icon" transform="translate(-8,-8) scale(0.67)">
              @switch (iconKind(node.code)) {
                @case ('power') {
                  <path d="M13 2 4 14h7l-2 8 11-14h-7l2-6z" />
                }
                @case ('cpu') {
                  <rect x="7" y="7" width="10" height="10" rx="1" />
                  <path d="M9 3v3M15 3v3M9 18v3M15 18v3M3 9h3M3 15h3M18 9h3M18 15h3" />
                }
                @case ('hdd') {
                  <ellipse cx="12" cy="12" rx="8" ry="8" />
                  <ellipse cx="12" cy="12" rx="3" ry="3" />
                }
                @case ('ram') {
                  <rect x="3" y="8" width="18" height="8" rx="1" />
                  <path d="M6 8V6M10 8V6M14 8V6M18 8V6M6 16v2M10 16v2M14 16v2M18 16v2" />
                }
                @default {
                  <path d="M7 16a4 4 0 0 1 .4-8 5 5 0 0 1 9.5 1.4A3.5 3.5 0 0 1 18 16Z" />
                }
              }
            </g>
            <circle class="status-dot" cx="11" cy="11" r="3.5" [attr.fill]="statusColor(node)" />
          </g>
        }
      </svg>
    }
  `,
  styles: [
    `
      .empty { margin: 0; font-size: 0.875rem; color: var(--text-muted); }
      .sankey { width: 100%; height: 20rem; display: block; }
      .ribbon {
        stroke: none;
      }
      .ribbon-label {
        fill: var(--text-primary);
        font-size: 11px;
        font-weight: 600;
        pointer-events: none;
        dominant-baseline: middle;
        letter-spacing: 0.02em;
      }
      .icon-ring {
        fill: var(--bg-card);
        stroke: var(--border);
        stroke-width: 1.5;
      }
      .icon path, .icon rect, .icon ellipse {
        fill: none;
        stroke: var(--text-secondary);
        stroke-width: 1.75;
        stroke-linejoin: round;
        stroke-linecap: round;
      }
      .status-dot { stroke: var(--bg-card); stroke-width: 1.5; }
    `,
  ],
})
export class SankeyDiagramComponent {
  @Input() width = 720;
  @Input() height = 320;

  layout: SankeyLayout | null = null;
  readonly originFill = SANKEY_RIBBON;

  get origin(): SankeyLayoutNode | undefined {
    return this.layout?.nodes.find((n) => n.kind === 'product');
  }

  get terminals(): SankeyLayoutNode[] {
    if (!this.layout) return [];
    return this.layout.nodes.filter((n) => n.kind !== 'product');
  }

  @Input()
  set graph(value: SankeyGraph | null | undefined) {
    this.layout = value ? layoutSankey(value, this.width, this.height) : null;
  }

  terminalX(node: SankeyLayoutNode): number {
    return node.x1 + 28;
  }

  terminalY(node: SankeyLayoutNode): number {
    return (node.y0 + node.y1) / 2;
  }

  iconKind(code: string): IconKind {
    const c = (code ?? '').toUpperCase();
    if (c.includes('POWER') || c.includes('PWR')) return 'power';
    if (c.includes('CPU')) return 'cpu';
    if (c.includes('HDD') || c.includes('DISK') || c.includes('STORAGE')) return 'hdd';
    if (c.includes('RAM') || c.includes('MEM')) return 'ram';
    return 'cloud';
  }

  statusColor(node: SankeyLayoutNode): string {
    if (node.damage <= 0) return '#22c55e';
    if (node.damage < 10) return '#eab308';
    if (node.damage < 25) return '#ea580c';
    return '#dc2626';
  }

  ribbonGradientId(link: SankeyLayoutLink): string {
    return `ribbon-grad-${link.from}-${link.to}`.replace(/[^a-zA-Z0-9_-]/g, '_');
  }
}

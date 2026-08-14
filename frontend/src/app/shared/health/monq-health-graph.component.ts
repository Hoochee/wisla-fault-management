import { Component, Input } from '@angular/core';
import { HealthLevel, ProductHealthDetail } from '../../core/api/api.models';
import { HEALTH_LABELS, percentToLevel } from '../../core/health/health-profile.util';
import { SankeyDiagramComponent } from './sankey-diagram.component';

@Component({
  selector: 'app-monq-health-graph',
  standalone: true,
  imports: [SankeyDiagramComponent],
  template: `
    <div class="monq-health-graph">
      <aside class="health-summary-card">
        <div class="card-kicker">
          <span class="type-icon" aria-hidden="true">▣</span>
          <span class="card-title">{{ product.name }}</span>
        </div>
        <div class="health-score" [attr.data-level]="level">
          {{ product.healthPercent ?? '—' }}%
        </div>
        <div class="severity" [attr.data-level]="level">{{ HEALTH_LABELS[level] }}</div>
        <div class="damage-box">
          <div class="damage-label">Анализ урона</div>
          <div class="damage-value">{{ damageText }}</div>
        </div>
        <div class="range">
          Мин <strong>{{ product.minHealthToday ?? '—' }}%</strong>
          —
          Макс <strong>{{ product.maxHealthToday ?? '—' }}%</strong>
        </div>
        <button type="button" class="rca-btn" disabled title="RCA — в разработке">RCA</button>
      </aside>
      <div class="graph-svg">
        <app-sankey-diagram [graph]="product.sankey" />
      </div>
    </div>
  `,
  styles: [
    `
      .monq-health-graph {
        display: flex;
        align-items: stretch;
        gap: 0;
        background: var(--bg-card);
        border: 1px solid var(--border);
        border-radius: 8px;
        overflow: hidden;
        min-height: 20rem;
      }
      .health-summary-card {
        position: relative;
        width: 16.5rem;
        flex-shrink: 0;
        padding: 1.25rem 0.55rem 1.25rem 1.15rem;
        border-right: none;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .card-kicker {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .type-icon {
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 8px;
        border: 1px solid var(--border);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        color: var(--text-secondary);
        background: var(--bg-primary);
      }
      .card-title {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--text-primary);
      }
      .health-score {
        font-size: 2.25rem;
        font-weight: 700;
        line-height: 1.1;
        letter-spacing: -0.03em;
      }
      .health-score[data-level='ok'] { color: #22c55e; }
      .health-score[data-level='warning'] { color: #ca8a04; }
      .health-score[data-level='major'] { color: #ea580c; }
      .health-score[data-level='fatal'],
      .health-score[data-level='critical'] { color: #dc2626; }
      .health-score[data-level='unknown'] { color: var(--text-muted); }
      .severity {
        font-size: 0.875rem;
        font-weight: 600;
        margin-top: -0.25rem;
      }
      .severity[data-level='ok'] { color: #22c55e; }
      .severity[data-level='warning'] { color: #ca8a04; }
      .severity[data-level='major'] { color: #ea580c; }
      .severity[data-level='fatal'],
      .severity[data-level='critical'] { color: #dc2626; }
      .damage-box {
        margin-top: 0.5rem;
        padding: 0.65rem 0.75rem;
        border-radius: 8px;
        background: #f8e4d4;
        border: 1px solid #edc9a8;
      }
      .damage-label {
        font-size: 0.6875rem;
        color: #9a5a32;
        margin-bottom: 0.125rem;
      }
      .damage-value {
        font-size: 1.375rem;
        font-weight: 700;
        color: #c2410c;
        letter-spacing: -0.02em;
      }
      .range {
        font-size: 0.75rem;
        color: var(--text-secondary);
      }
      .range strong { color: var(--text-primary); font-weight: 600; }
      .rca-btn {
        margin-top: auto;
        align-self: flex-start;
        padding: 0.375rem 0.75rem;
        font-size: 0.75rem;
        border-radius: 8px;
        border: 1px solid var(--border);
        background: var(--bg-card);
        color: var(--text-muted);
        cursor: not-allowed;
      }
      .graph-svg {
        flex: 1;
        min-width: 0;
        padding: 0;
        margin-left: -2px;
        position: relative;
        z-index: 1;
      }
      @media (max-width: 900px) {
        .monq-health-graph { flex-direction: column; }
        .health-summary-card { width: auto; border-bottom: 1px solid var(--border); }
      }
    `,
  ],
})
export class MonqHealthGraphComponent {
  readonly HEALTH_LABELS = HEALTH_LABELS;

  @Input({ required: true }) product!: ProductHealthDetail;

  get level(): HealthLevel {
    return percentToLevel(this.product.healthPercent ?? 100);
  }

  get damageText(): string {
    const d = this.product.damagePercent;
    if (d == null) return '—';
    const n = Number.isInteger(d) ? String(d) : d.toFixed(2);
    return `−${n}%`;
  }
}

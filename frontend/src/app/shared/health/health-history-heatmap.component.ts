import { Component, Input } from '@angular/core';
import { ProductHealthHistoryBucket } from '../../core/api/api.models';
import { getHealthPercentColor } from '../../core/health/health-profile.util';

@Component({
  selector: 'app-health-history-heatmap',
  standalone: true,
  template: `
    <div class="panel">
      <div class="head">
        <h3>История здоровья</h3>
        <span class="hint">бакет {{ bucketLabel }}</span>
      </div>
      @if (buckets.length === 0) {
        <p class="empty">Нет данных истории</p>
      } @else {
        <div class="strip">
          @for (b of buckets; track b.bucketStart) {
            <div
              class="cell"
              [style.background-color]="getHealthPercentColor(b.minHealth)"
              [title]="tooltip(b)"
            ></div>
          }
        </div>
        <div class="legend">
          <span>хуже</span>
          <span class="grad"></span>
          <span>лучше</span>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .head { display: flex; justify-content: space-between; margin-bottom: 0.75rem; }
      h3 { margin: 0; font-size: 0.875rem; color: var(--text-primary); font-weight: 500; }
      .hint { font-size: 0.75rem; color: var(--text-muted); }
      .empty { margin: 0; font-size: 0.875rem; color: var(--text-muted); }
      .strip { display: flex; gap: 2px; height: 12px; flex-wrap: wrap; }
      .cell { width: 10px; height: 10px; flex: 0 0 10px; border-radius: 2px; }
      .legend { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.5rem; font-size: 0.6875rem; color: var(--text-muted); }
      .grad {
        flex: 1; height: 6px; border-radius: 3px;
        background: linear-gradient(90deg, #dc2626, #ea580c, #eab308, #22c55e);
      }
    `,
  ],
})
export class HealthHistoryHeatmapComponent {
  @Input() buckets: ProductHealthHistoryBucket[] = [];
  readonly getHealthPercentColor = getHealthPercentColor;

  get bucketLabel(): string {
    return this.buckets[0] ? `${this.buckets[0].bucketMinutes} мин` : '15 мин';
  }

  tooltip(b: ProductHealthHistoryBucket): string {
    return `${b.bucketStart}: min ${b.minHealth}% · max ${b.maxHealth}% · ${b.worstSeverity}`;
  }
}

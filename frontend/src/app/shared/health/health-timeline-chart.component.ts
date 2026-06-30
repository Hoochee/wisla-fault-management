import { Component, Input } from '@angular/core';
import { HealthTimelinePoint } from '../../core/health/health.models';
import { HealthBadgeComponent } from './health-badge.component';

@Component({
  selector: 'app-health-timeline-chart',
  standalone: true,
  imports: [HealthBadgeComponent],
  template: `
    <div class="panel">
      <div class="head">
        <h3>График здоровья за сутки</h3>
        <span class="hint">мин. {{ minPercent }}%</span>
      </div>
      <div class="chart">
        @for (point of timeline; track $index) {
          <div class="bar-col" [title]="point.time + ': ' + point.percent + '%'">
            <div class="bar" [style.height.%]="point.percent" [style.background-color]="barColor(point.level)"></div>
            @if ($index % 2 === 0) {
              <span class="time">{{ point.time }}</span>
            }
          </div>
        }
      </div>
      <div class="footer">
        <span class="hint">Текущее:</span>
        @if (timeline.length) {
          <app-health-badge
            [level]="timeline[timeline.length - 1].level"
            [percent]="timeline[timeline.length - 1].percent"
            [compact]="true"
          />
        }
      </div>
    </div>
  `,
  styles: [
    `
      .panel { background: #252830; border: 1px solid #3a3f4b; border-radius: 8px; padding: 1rem; }
      .head { display: flex; justify-content: space-between; margin-bottom: 0.75rem; }
      h3 { margin: 0; font-size: 0.875rem; color: #fff; font-weight: 500; }
      .hint { font-size: 0.75rem; color: #6b7280; }
      .chart { display: flex; align-items: flex-end; gap: 0.25rem; height: 8rem; }
      .bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 0.25rem; min-width: 0; }
      .bar { width: 100%; border-radius: 4px 4px 0 0; min-height: 4px; }
      .time { font-size: 9px; color: #6b7280; text-align: center; width: 100%; overflow: hidden; text-overflow: ellipsis; }
      .footer { display: flex; align-items: center; gap: 0.75rem; margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid #3a3f4b; }
    `,
  ],
})
export class HealthTimelineChartComponent {
  @Input({ required: true }) timeline!: HealthTimelinePoint[];

  get minPercent(): number {
    return this.timeline.length ? Math.min(...this.timeline.map((p) => p.percent)) : 0;
  }

  barColor(level: HealthTimelinePoint['level']): string {
    if (level === 'fatal' || level === 'critical') return '#dc2626';
    if (level === 'major') return '#ea580c';
    if (level === 'warning') return '#eab308';
    if (level === 'ok') return '#22c55e';
    return '#6b7280';
  }
}

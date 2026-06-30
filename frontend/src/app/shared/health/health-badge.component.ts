import { Component, Input } from '@angular/core';
import { HealthLevel } from '../../core/api/api.models';
import { HEALTH_LABELS } from '../../core/health/health-profile.util';

@Component({
  selector: 'app-health-badge',
  standalone: true,
  template: `
    @if (compact) {
      <span class="compact">
        <span class="dot" [attr.data-level]="level"></span>
        <span class="label" [attr.data-level]="level">
          {{ HEALTH_LABELS[level] }}
          @if (percent !== undefined) {
            <span> · {{ percent }}%</span>
          }
        </span>
      </span>
    } @else {
      <span class="badge" [attr.data-level]="level">
        {{ HEALTH_LABELS[level] }}
        @if (percent !== undefined) {
          <span> · {{ percent }}%</span>
        }
      </span>
    }
  `,
  styles: [
    `
      .compact { display: inline-flex; align-items: center; gap: 0.375rem; }
      .dot { width: 8px; height: 8px; border-radius: 50%; }
      .dot[data-level='fatal'], .dot[data-level='critical'] { background: #dc2626; }
      .dot[data-level='major'] { background: #ea580c; }
      .dot[data-level='warning'] { background: #eab308; }
      .dot[data-level='ok'] { background: #22c55e; }
      .dot[data-level='unknown'] { background: #6b7280; }
      .label { font-size: 0.75rem; }
      .label[data-level='fatal'], .label[data-level='critical'] { color: #dc2626; }
      .label[data-level='major'] { color: #ea580c; }
      .label[data-level='warning'] { color: #eab308; }
      .label[data-level='ok'] { color: #22c55e; }
      .label[data-level='unknown'] { color: #6b7280; }
      .badge {
        display: inline-flex;
        align-items: center;
        padding: 0.125rem 0.5rem;
        border-radius: 4px;
        font-size: 0.75rem;
        font-weight: 500;
        color: #fff;
      }
      .badge[data-level='fatal'], .badge[data-level='critical'] { background: #dc2626; }
      .badge[data-level='major'] { background: #ea580c; }
      .badge[data-level='warning'] { background: #eab308; color: #1a1d23; }
      .badge[data-level='ok'] { background: #22c55e; }
      .badge[data-level='unknown'] { background: #6b7280; }
    `,
  ],
})
export class HealthBadgeComponent {
  readonly HEALTH_LABELS = HEALTH_LABELS;
  @Input({ required: true }) level!: HealthLevel;
  @Input() compact = false;
  @Input() percent?: number;
}

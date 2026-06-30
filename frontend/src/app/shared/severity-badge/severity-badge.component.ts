import { Component, Input } from '@angular/core';
import { Severity } from '../../core/api/api.models';

const SEVERITY_COLORS: Record<Severity, string> = {
  fatal: '#dc2626',
  critical: '#dc2626',
  major: '#ea580c',
  minor: '#ca8a04',
  warning: '#eab308',
  normal: '#3b82f6',
};

const SEVERITY_LABELS: Record<Severity, string> = {
  fatal: 'Авария',
  critical: 'Критично',
  major: 'Существенно',
  minor: 'Незначительно',
  warning: 'Предупреждение',
  normal: 'Норма',
};

@Component({
  selector: 'app-severity-badge',
  standalone: true,
  template: `
    @if (compact) {
      <span class="compact">
        <span class="dot" [style.background-color]="color"></span>
        <span class="label">{{ SEVERITY_LABELS[severity] }}</span>
      </span>
    } @else {
      <span class="badge" [style.background-color]="color">{{ SEVERITY_LABELS[severity] }}</span>
    }
  `,
  styles: [
    `
      .compact { display: inline-flex; align-items: center; gap: 0.375rem; }
      .dot { width: 8px; height: 8px; border-radius: 50%; }
      .label { font-size: 0.75rem; color: #d1d5db; }
      .badge {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        color: #fff;
      }
    `,
  ],
})
export class SeverityBadgeComponent {
  readonly SEVERITY_LABELS = SEVERITY_LABELS;
  @Input({ required: true }) severity!: Severity;
  @Input() compact = false;

  get color(): string {
    return SEVERITY_COLORS[this.severity] ?? '#9e9e9e';
  }
}

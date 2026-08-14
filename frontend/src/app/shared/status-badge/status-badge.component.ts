import { Component, Input } from '@angular/core';
import { EventStatus } from '../../core/api/api.models';

const STATUS_LABELS: Record<EventStatus, string> = {
  new: 'Новое',
  in_progress: 'В работе',
  closed: 'Закрыто',
  archived: 'Архив',
  maintenance: 'Обслуживание',
  deferred: 'Отложено',
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="badge" [class]="status">{{ label }}</span>`,
  styles: [
    `
      .badge {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 11px;
        font-weight: 500;
      }
      .new { background: #e8f0fe; color: var(--accent); }
      .in_progress { background: #fff8e1; color: #b45309; }
      .closed { background: #f3f4f6; color: var(--text-muted); }
      .archived { background: #f3f4f6; color: var(--text-muted); }
      .maintenance { background: #e8f5e9; color: #15803d; }
      .deferred { background: #fff3e0; color: #c2410c; }
    `,
  ],
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: EventStatus;

  get label(): string {
    return STATUS_LABELS[this.status] ?? this.status;
  }
}

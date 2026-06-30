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
      .new { background: #1e3a5f; color: #4a9eff; }
      .in_progress { background: #3d3520; color: #ffc107; }
      .closed { background: #2a2f3a; color: #9e9e9e; }
      .archived { background: #2a2f3a; color: #757575; }
      .maintenance { background: #1b3d2f; color: #4caf50; }
      .deferred { background: #3d2a1b; color: #ff9800; }
    `,
  ],
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: EventStatus;

  get label(): string {
    return STATUS_LABELS[this.status] ?? this.status;
  }
}
